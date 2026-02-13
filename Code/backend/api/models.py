from django.db import models
from django.contrib.auth.models import AbstractUser

class Student(AbstractUser):
    current_xp = models.IntegerField(default=0)
    level = models.IntegerField(default=1)
    credits = models.IntegerField(default=0)

    groups = models.ManyToManyField(
        'auth.Group',
        related_name='student_set',
        blank=True,
        help_text='The groups this user belongs to.',
        verbose_name='groups',
    )
    user_permissions = models.ManyToManyField(
        'auth.Permission',
        related_name='student_set',
        blank=True,
        help_text='Specific permissions for this user.',
        verbose_name='user permissions',
    )

class Task(models.Model):
    title = models.CharField(max_length=200)
    is_completed = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    due_date = models.DateTimeField(null=True, blank=True)
    user = models.ForeignKey(Student, on_delete=models.CASCADE, related_name='tasks')

    def __str__(self):
        return self.title