from django.db import models
from django.contrib.auth.models import AbstractUser

class Student(AbstractUser):
    current_xp = models.IntegerField(default=0)
    level = models.IntegerField(default=1)
    credits = models.IntegerField(default=0)

    def __str__(self):
        return self.username

class Task(models.Model):
    user = models.ForeignKey(Student, on_delete=models.CASCADE, related_name='tasks') # 关联到用户
    title = models.CharField(max_length=255) 
    is_completed = models.BooleanField(default=False) 
    created_at = models.DateTimeField(auto_now_add=True) 

    def __str__(self):
        return self.title