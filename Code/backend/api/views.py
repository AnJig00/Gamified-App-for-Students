from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework.permissions import AllowAny, IsAuthenticated
from django.contrib.auth import authenticate
from .models import Task, Student 
from .serializers import StudentSerializer, TaskSerializer

@api_view(['POST'])
@permission_classes([AllowAny])
def register_user(request):
    serializer = StudentSerializer(data=request.data)
    if serializer.is_valid():
        serializer.save()
        return Response({"message": "Account created successfully!", **serializer.data}, status=status.HTTP_201_CREATED)
    return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

@api_view(['POST'])
def login_user(request):
    username = request.data.get('username')
    password = request.data.get('password')
    user = authenticate(username=username, password=password)
    if user is not None:
        return Response({
            "message": "Login successful!",
            "user_id": user.id,
            "username": user.username,
            "current_xp": user.current_xp, 
            "level": user.level
        }, status=status.HTTP_200_OK)
    else:
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

    elif request.method == 'POST':
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
                owner.current_xp += 10 
                owner.credits += 10    
                owner.save()
                return Response({
                    "data": serializer.data,
                    "message": "Task Completed! +10 XP",
                    "new_xp": owner.current_xp
                })
            
            return Response(serializer.data)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

    elif request.method == 'DELETE':
        task.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)

class LeaderboardView(APIView):
    def get(self, request):
        users = Student.objects.all().order_by('-current_xp')[:10]
        data = []
        for user in users:
            data.append({
                "username": user.username,
                "xp": user.current_xp,
                "level": user.level
            })
        return Response(data)
    
@api_view(['POST'])
def add_focus_log(request):
    user_id = request.data.get('user_id')
    minutes = request.data.get('minutes')

    if not user_id or not minutes:
        return Response({"error": "Missing data"}, status=status.HTTP_400_BAD_REQUEST)

    try:
        student = Student.objects.get(pk=user_id)
        xp_earned = int(minutes) * 1 #You can change the rate here， 1 XP per minute
        student.current_xp += xp_earned
        student.save()
        
        return Response({
            "message": f"Focus Session Complete! +{xp_earned} XP",
            "new_xp": student.current_xp
        }, status=status.HTTP_200_OK)
    except Student.DoesNotExist:
        return Response({"error": "User not found"}, status=status.HTTP_404_NOT_FOUND)