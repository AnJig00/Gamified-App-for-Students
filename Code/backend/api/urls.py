from django.urls import path
from . import views
from .views import LeaderboardView, LeagueLeaderboardView, LeagueStatusView, ProfileView

urlpatterns = [
    path('register/', views.register_user, name='register'),
    path('login/', views.login_user, name='login'),

    path('tasks/', views.task_list_create, name='task-list-create'),
    path('tasks/<int:pk>/', views.task_detail, name='task-detail'),

    path('timetable/', views.timetable_list_create, name='timetable-list-create'),
    path('timetable/<int:pk>/', views.timetable_detail, name='timetable-detail'),

    path('notes/', views.note_list_create, name='note-list-create'),
    path('notes/<int:pk>/', views.note_detail, name='note-detail'),

    path('leaderboard/', LeaderboardView.as_view(), name='leaderboard'),
    path('profile/', ProfileView.as_view(), name='profile'),
    path('league/status/', LeagueStatusView.as_view(), name='league-status'),
    path('league/leaderboard/', LeagueLeaderboardView.as_view(), name='league-leaderboard'),

    path('focus/', views.add_focus_log, name='focus-log'),
]
