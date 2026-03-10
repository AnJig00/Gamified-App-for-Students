from django.contrib import admin

from .models import Task, TimetableEntry


@admin.register(Task)
class TaskAdmin(admin.ModelAdmin):
    list_display = ('title', 'user', 'is_completed', 'created_at')
    list_filter = ('is_completed', 'user')
    search_fields = ('title', 'user__username')


@admin.register(TimetableEntry)
class TimetableEntryAdmin(admin.ModelAdmin):
    list_display = ('course_name', 'user', 'day_of_week', 'start_time', 'end_time', 'classroom')
    list_filter = ('day_of_week', 'user')
    search_fields = ('course_name', 'classroom', 'user__username')
