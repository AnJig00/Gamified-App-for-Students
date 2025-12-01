from django.urls import path
from . import views

urlpatterns = [
    path('register/', views.register_user, name='register'),
    path('login/', views.login_user, name='login'),
    
    path('tasks/', views.task_list_create, name='task-list-create'), 
    path('tasks/<int:pk>/', views.task_detail, name='task-detail'),  
]