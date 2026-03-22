from django.contrib.auth import authenticate
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from .league_services import (
    award_student_xp,
    ensure_league_membership,
    ensure_weekly_entry,
    get_current_league_week,
    get_league_leaderboard_for_student,
    get_profile_snapshot,
    get_league_status_for_student,
)
from .models import Note, Student, Task, TimetableEntry
from .serializers import NoteSerializer, StudentSerializer, TaskSerializer, TimetableEntrySerializer


@api_view(['POST'])
@permission_classes([AllowAny])
def register_user(request):
    serializer = StudentSerializer(data=request.data)
    if serializer.is_valid():
        serializer.save()
        return Response(
            {"message": "Account created successfully!", **serializer.data},
            status=status.HTTP_201_CREATED,
        )
    return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


@api_view(['POST'])
def login_user(request):
    username = request.data.get('username')
    password = request.data.get('password')
    user = authenticate(username=username, password=password)
    if user is not None:
        ensure_league_membership(user)
        ensure_weekly_entry(user, get_current_league_week())
        return Response(
            {
                "message": "Login successful!",
                "user_id": user.id,
                "username": user.username,
                "current_xp": user.current_xp,
                "level": user.level,
            },
            status=status.HTTP_200_OK,
        )
    return Response({"error": "Invalid credentials"}, status=status.HTTP_401_UNAUTHORIZED)


@api_view(['GET', 'POST'])
def task_list_create(request):
    user_id = request.query_params.get('user_id')

    if not user_id:
        return Response({"error": "User ID is required"}, status=status.HTTP_400_BAD_REQUEST)

    try:
        user = Student.objects.get(pk=user_id)
    except Student.DoesNotExist:
        return Response({"error": "User not found"}, status=status.HTTP_404_NOT_FOUND)

    if request.method == 'GET':
        tasks = Task.objects.filter(user=user)
        serializer = TaskSerializer(tasks, many=True)
        return Response(serializer.data)

    serializer = TaskSerializer(data=request.data)
    if serializer.is_valid():
        serializer.save(user=user)
        return Response(serializer.data, status=status.HTTP_201_CREATED)
    return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


@api_view(['PATCH', 'DELETE'])
def task_detail(request, pk):
    user_id = request.query_params.get('user_id')
    if not user_id:
        return Response({"error": "User ID is required"}, status=status.HTTP_400_BAD_REQUEST)

    try:
        task = Task.objects.get(pk=pk, user__id=user_id)
    except Task.DoesNotExist:
        return Response(status=status.HTTP_404_NOT_FOUND)

    if request.method == 'PATCH':
        was_completed = task.is_completed
        serializer = TaskSerializer(task, data=request.data, partial=True)
        if serializer.is_valid():
            updated_task = serializer.save()

            if updated_task.is_completed and not was_completed:
                owner = Student.objects.get(pk=user_id)
                owner.credits += 10
                owner.save(update_fields=['credits'])
                weekly_entry = award_student_xp(owner, 10)
                return Response(
                    {
                        "data": serializer.data,
                        "message": "Task Completed! +10 XP",
                        "new_xp": owner.current_xp,
                        "weekly_xp": weekly_entry.weekly_xp,
                    }
                )

            return Response(serializer.data)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

    task.delete()
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(['GET', 'POST'])
def timetable_list_create(request):
    user_id = request.query_params.get('user_id')

    if not user_id:
        return Response({"error": "User ID is required"}, status=status.HTTP_400_BAD_REQUEST)

    try:
        user = Student.objects.get(pk=user_id)
    except Student.DoesNotExist:
        return Response({"error": "User not found"}, status=status.HTTP_404_NOT_FOUND)

    if request.method == 'GET':
        entries = TimetableEntry.objects.filter(user=user)
        serializer = TimetableEntrySerializer(entries, many=True)
        return Response(serializer.data)

    serializer = TimetableEntrySerializer(data=request.data)
    if serializer.is_valid():
        serializer.save(user=user)
        return Response(serializer.data, status=status.HTTP_201_CREATED)
    return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


@api_view(['PATCH', 'DELETE'])
def timetable_detail(request, pk):
    user_id = request.query_params.get('user_id')
    if not user_id:
        return Response({"error": "User ID is required"}, status=status.HTTP_400_BAD_REQUEST)

    try:
        entry = TimetableEntry.objects.get(pk=pk, user__id=user_id)
    except TimetableEntry.DoesNotExist:
        return Response(status=status.HTTP_404_NOT_FOUND)

    if request.method == 'PATCH':
        serializer = TimetableEntrySerializer(entry, data=request.data, partial=True)
        if serializer.is_valid():
            serializer.save()
            return Response(serializer.data)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

    entry.delete()
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(['GET', 'POST'])
def note_list_create(request):
    student, error_response = get_student_from_query_params(request)
    if error_response is not None:
        return error_response

    if request.method == 'GET':
        notes = Note.objects.filter(user=student)

        linked_task_id = request.query_params.get('linked_task_id')
        linked_timetable_entry_id = request.query_params.get('linked_timetable_entry_id')
        note_type = request.query_params.get('note_type')
        course_name = request.query_params.get('course_name')
        query = request.query_params.get('query')

        if linked_task_id:
            notes = notes.filter(linked_task_id=linked_task_id)
        if linked_timetable_entry_id:
            notes = notes.filter(linked_timetable_entry_id=linked_timetable_entry_id)
        if note_type:
            notes = notes.filter(note_type=note_type)
        if course_name:
            notes = notes.filter(course_name__iexact=course_name)
        if query:
            notes = notes.filter(title__icontains=query) | notes.filter(content_markdown__icontains=query)

        serializer = NoteSerializer(notes.distinct(), many=True)
        return Response(serializer.data)

    serializer = NoteSerializer(data=request.data, context={'user': student})
    if serializer.is_valid():
        serializer.save(user=student)
        return Response(serializer.data, status=status.HTTP_201_CREATED)
    return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


@api_view(['PATCH', 'DELETE'])
def note_detail(request, pk):
    user_id = request.query_params.get('user_id')
    if not user_id:
        return Response({"error": "User ID is required"}, status=status.HTTP_400_BAD_REQUEST)

    try:
        note = Note.objects.get(pk=pk, user__id=user_id)
    except Note.DoesNotExist:
        return Response(status=status.HTTP_404_NOT_FOUND)

    if request.method == 'PATCH':
        serializer = NoteSerializer(
            note,
            data=request.data,
            partial=True,
            context={'user': note.user},
        )
        if serializer.is_valid():
            serializer.save()
            return Response(serializer.data)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

    note.delete()
    return Response(status=status.HTTP_204_NO_CONTENT)


class LeaderboardView(APIView):
    def get(self, request):
        users = Student.objects.all().order_by('-current_xp')[:10]
        data = []
        for user in users:
            data.append(
                {
                    "username": user.username,
                    "xp": user.current_xp,
                    "level": user.level,
                }
            )
        return Response(data)


def get_student_from_query_params(request):
    user_id = request.query_params.get('user_id')
    if not user_id:
        return None, Response({"error": "User ID is required"}, status=status.HTTP_400_BAD_REQUEST)

    try:
        student = Student.objects.get(pk=user_id)
    except Student.DoesNotExist:
        return None, Response({"error": "User not found"}, status=status.HTTP_404_NOT_FOUND)

    return student, None


class LeagueStatusView(APIView):
    def get(self, request):
        student, error_response = get_student_from_query_params(request)
        if error_response is not None:
            return error_response

        return Response(get_league_status_for_student(student))


class LeagueLeaderboardView(APIView):
    def get(self, request):
        student, error_response = get_student_from_query_params(request)
        if error_response is not None:
            return error_response

        return Response(get_league_leaderboard_for_student(student))


class ProfileView(APIView):
    def get(self, request):
        student, error_response = get_student_from_query_params(request)
        if error_response is not None:
            return error_response

        return Response(get_profile_snapshot(student))


@api_view(['POST'])
def add_focus_log(request):
    user_id = request.data.get('user_id')
    minutes = request.data.get('minutes')

    if not user_id or not minutes:
        return Response({"error": "Missing data"}, status=status.HTTP_400_BAD_REQUEST)

    try:
        student = Student.objects.get(pk=user_id)
        xp_earned = int(minutes)
        weekly_entry = award_student_xp(student, xp_earned)

        return Response(
            {
                "message": f"Focus Session Complete! +{xp_earned} XP",
                "new_xp": student.current_xp,
                "weekly_xp": weekly_entry.weekly_xp,
            },
            status=status.HTTP_200_OK,
        )
    except Student.DoesNotExist:
        return Response({"error": "User not found"}, status=status.HTTP_404_NOT_FOUND)
