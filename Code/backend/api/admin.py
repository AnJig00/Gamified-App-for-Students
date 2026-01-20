from django.contrib import admin
from .models import Task 

@admin.register(Task)
class TaskAdmin(admin.ModelAdmin):
    list_display = ('title', 'user', 'is_completed', 'created_at') # 列表页显示这些字段
    list_filter = ('is_completed', 'user') 
    search_fields = ('title', 'user__username') 