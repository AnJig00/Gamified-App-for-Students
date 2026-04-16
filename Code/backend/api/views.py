from django.contrib.auth import authenticate
from django.db.models import Q
from django.utils import timezone
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from .avatar_utils import get_avatar_url, validate_avatar_upload
from .league_services import (
    award_student_xp,
    ensure_league_membership,
    ensure_weekly_entry,
    get_current_league_week,
    get_league_leaderboard_for_student,
    get_profile_snapshot,
    get_league_status_for_student,
)
from .models import ConnectionRequest, Friendship, Note, Student, Task, TimetableEntry
from .serializers import (
    ConnectionRequestSerializer,
    FriendshipSerializer,
    NoteSerializer,
    SocialConnectRequestSerializer,
    SocialFriendshipRequestSerializer,
    SocialFriendSummarySerializer,
    SocialPresenceStartResponseSerializer,
    SocialResolveRequestSerializer,
    SocialResolveResponseSerializer,
    SocialRespondRequestSerializer,
    StudentSerializer,
    TaskSerializer,
    TimetableEntrySerializer,
)
from .social_services import (
    SOCIAL_SERVICE_UUID,
    SOCIAL_TOKEN_TTL_SECONDS,
    confirm_connection_request,
    deactivate_social_tokens,
    friendship_exists,
    get_canonical_friendship_pair,
    issue_social_token,
    resolve_social_token,
)


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

        return Response(get_profile_snapshot(student, request=request))


class ProfileAvatarUploadView(APIView):
    parser_classes = [MultiPartParser, FormParser]

    def post(self, request):
        student, error_response = get_student_from_query_params(request)
        if error_response is not None:
            return error_response

        avatar_file = request.FILES.get('avatar')
        validate_avatar_upload(avatar_file)

        old_avatar_name = student.avatar.name if student.avatar else None
        student.avatar.save(avatar_file.name, avatar_file, save=False)
        student.save(update_fields=['avatar'])

        if old_avatar_name and old_avatar_name != student.avatar.name:
            student.avatar.storage.delete(old_avatar_name)

        return Response(
            {
                'message': 'Avatar updated.',
                'avatar_url': get_avatar_url(student, request),
            },
            status=status.HTTP_200_OK,
        )


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


@api_view(['POST'])
def social_presence_start(request):
    student, error_response = get_student_from_query_params(request)
    if error_response is not None:
        return error_response

    if not student.social_discoverable:
        return Response(
            {"error": "Social discovery is disabled for this profile."},
            status=status.HTTP_403_FORBIDDEN,
        )

    raw_token, token = issue_social_token(student)
    serializer = SocialPresenceStartResponseSerializer(
        data={
            'token': raw_token,
            'expires_at': token.expires_at,
            'ttl_seconds': SOCIAL_TOKEN_TTL_SECONDS,
            'service_uuid': SOCIAL_SERVICE_UUID,
        }
    )
    serializer.is_valid(raise_exception=True)
    return Response(serializer.data, status=status.HTTP_201_CREATED)


@api_view(['POST'])
def social_presence_stop(request):
    student, error_response = get_student_from_query_params(request)
    if error_response is not None:
        return error_response

    deactivated = deactivate_social_tokens(student)
    return Response(
        {
            "message": "Social presence stopped.",
            "deactivated_tokens": deactivated,
        },
        status=status.HTTP_200_OK,
    )


@api_view(['POST'])
def social_resolve(request):
    serializer = SocialResolveRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    token, error_code = resolve_social_token(serializer.validated_data['token'])
    if token is None:
        error_mapping = {
            'invalid': ("Invalid social token.", status.HTTP_404_NOT_FOUND),
            'expired': ("Social token expired.", status.HTTP_410_GONE),
            'hidden': ("This student is not discoverable right now.", status.HTTP_403_FORBIDDEN),
        }
        message, status_code = error_mapping[error_code]
        return Response({"error": message}, status=status_code)

    response_serializer = SocialResolveResponseSerializer(
        data={
            'student_id': token.student.id,
            'username': token.student.username,
            'avatar_url': get_avatar_url(token.student, request),
            'department': token.student.department,
            'year_of_study': token.student.year_of_study,
        }
    )
    response_serializer.is_valid(raise_exception=True)
    return Response(response_serializer.data, status=status.HTTP_200_OK)


@api_view(['POST'])
def social_connect(request):
    student, error_response = get_student_from_query_params(request)
    if error_response is not None:
        return error_response

    serializer = SocialConnectRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    target_student_id = serializer.validated_data['target_student_id']
    rssi = serializer.validated_data.get('rssi')

    try:
        target_student = Student.objects.get(pk=target_student_id)
    except Student.DoesNotExist:
        return Response({"error": "Target student not found."}, status=status.HTTP_404_NOT_FOUND)

    if target_student.id == student.id:
        return Response(
            {"error": "You cannot send a connection request to yourself."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    if friendship_exists(student, target_student):
        return Response(
            {"error": "You are already friends with this student."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    existing_request = ConnectionRequest.objects.filter(
        from_student=student,
        to_student=target_student,
        status=ConnectionRequest.STATUS_PENDING,
    ).first()
    if existing_request is not None:
        return Response(
            {
                "message": "A pending request already exists.",
                "request": ConnectionRequestSerializer(existing_request, context={'request': request}).data,
            },
            status=status.HTTP_200_OK,
        )

    reverse_pending_request = ConnectionRequest.objects.filter(
        from_student=target_student,
        to_student=student,
        status=ConnectionRequest.STATUS_PENDING,
    ).first()
    if reverse_pending_request is not None:
        return Response(
            {
                "error": "This student already sent you a pending request.",
                "request": ConnectionRequestSerializer(reverse_pending_request, context={'request': request}).data,
            },
            status=status.HTTP_409_CONFLICT,
        )

    connection_request = ConnectionRequest.objects.create(
        from_student=student,
        to_student=target_student,
    )
    connection_request.encounters.create(
        initiator=student,
        target=target_student,
        rssi=rssi,
        confirmed=False,
    )

    return Response(
        {
            "message": "Connection request sent.",
            "request": ConnectionRequestSerializer(connection_request, context={'request': request}).data,
        },
        status=status.HTTP_201_CREATED,
    )


@api_view(['POST'])
def social_confirm(request):
    student, error_response = get_student_from_query_params(request)
    if error_response is not None:
        return error_response

    serializer = SocialRespondRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    try:
        connection_request = ConnectionRequest.objects.select_related(
            'from_student',
            'to_student',
        ).get(pk=serializer.validated_data['request_id'])
    except ConnectionRequest.DoesNotExist:
        return Response({"error": "Connection request not found."}, status=status.HTTP_404_NOT_FOUND)

    if connection_request.to_student_id != student.id:
        return Response(
            {"error": "Only the recipient can respond to this request."},
            status=status.HTTP_403_FORBIDDEN,
        )

    if serializer.validated_data['action'] == 'reject':
        if connection_request.status != ConnectionRequest.STATUS_PENDING:
            return Response(
                {"error": "This request has already been resolved."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        connection_request.status = ConnectionRequest.STATUS_REJECTED
        connection_request.responded_at = timezone.now()
        connection_request.save(update_fields=['status', 'responded_at'])
        return Response(
            {
                "message": "Connection request rejected.",
                "request": ConnectionRequestSerializer(connection_request, context={'request': request}).data,
            },
            status=status.HTTP_200_OK,
        )

    encounter, xp_awarded = confirm_connection_request(connection_request)
    if encounter is None:
        return Response(
            {"error": "This request has already been resolved."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    student.refresh_from_db(fields=['current_xp', 'level'])
    connection_request.from_student.refresh_from_db(fields=['current_xp', 'level'])

    return Response(
        {
            "message": "Connection request accepted.",
            "request": ConnectionRequestSerializer(connection_request, context={'request': request}).data,
            "encounter_id": encounter.id,
            "xp_awarded": xp_awarded,
            "recipient_new_xp": student.current_xp,
            "sender_new_xp": connection_request.from_student.current_xp,
        },
        status=status.HTTP_200_OK,
    )


@api_view(['GET'])
def social_requests(request):
    student, error_response = get_student_from_query_params(request)
    if error_response is not None:
        return error_response

    incoming = ConnectionRequest.objects.filter(to_student=student).select_related(
        'from_student',
        'to_student',
    )
    outgoing = ConnectionRequest.objects.filter(from_student=student).select_related(
        'from_student',
        'to_student',
    )

    return Response(
        {
            "incoming": ConnectionRequestSerializer(incoming, many=True, context={'request': request}).data,
            "outgoing": ConnectionRequestSerializer(outgoing, many=True, context={'request': request}).data,
        }
    )


def serialize_friendship_for_student(student, friendship, request=None):
    friend = friendship.student_b if friendship.student_a_id == student.id else friendship.student_a
    serializer = SocialFriendSummarySerializer(
        data={
            'id': friendship.id,
            'friend_id': friend.id,
            'username': friend.username,
            'avatar_url': get_avatar_url(friend, request),
            'department': friend.department,
            'year_of_study': friend.year_of_study,
            'created_at': friendship.created_at,
        }
    )
    serializer.is_valid(raise_exception=True)
    return serializer.data


@api_view(['GET', 'POST'])
def social_add_friend(request):
    student, error_response = get_student_from_query_params(request)
    if error_response is not None:
        return error_response

    if request.method == 'GET':
        friendships = Friendship.objects.filter(
            Q(student_a=student) | Q(student_b=student)
        ).select_related('student_a', 'student_b')
        return Response(
            {
                "friends": [
                    serialize_friendship_for_student(student, friendship, request=request)
                    for friendship in friendships
                ]
            }
        )

    serializer = SocialFriendshipRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    try:
        connection_request = ConnectionRequest.objects.select_related(
            'from_student',
            'to_student',
        ).get(pk=serializer.validated_data['request_id'])
    except ConnectionRequest.DoesNotExist:
        return Response({"error": "Connection request not found."}, status=status.HTTP_404_NOT_FOUND)

    if student.id not in {connection_request.from_student_id, connection_request.to_student_id}:
        return Response(
            {"error": "Only participants can create a friendship from this request."},
            status=status.HTTP_403_FORBIDDEN,
        )

    if connection_request.status != ConnectionRequest.STATUS_ACCEPTED:
        return Response(
            {"error": "You can only add a friend after the connection request is accepted."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    student_a, student_b = get_canonical_friendship_pair(
        connection_request.from_student,
        connection_request.to_student,
    )
    friendship, created = Friendship.objects.get_or_create(
        student_a=student_a,
        student_b=student_b,
    )

    return Response(
        {
            "message": "Friend added." if created else "Friendship already exists.",
            "friendship": FriendshipSerializer(friendship).data,
        },
        status=status.HTTP_201_CREATED if created else status.HTTP_200_OK,
    )
