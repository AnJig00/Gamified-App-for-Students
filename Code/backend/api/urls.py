from django.urls import path, include

from backend.api import admin
from . import views
from .views import LeaderboardView  

urlpatterns = [
    path('register/', views.register_user, name='register'),
    path('login/', views.login_user, name='login'),
    
    path('tasks/', views.task_list_create, name='task-list-create'), 
    path('tasks/<int:pk>/', views.task_detail, name='task-detail'),  
    
    path('leaderboard/', LeaderboardView.as_view(), name='leaderboard'),

    path('focus/', views.add_focus_log, name='focus-log'),

    path('admin/', admin.site.urls),
    path('api/', include('api.urls')),
]