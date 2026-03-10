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


class TimetableEntry(models.Model):
    DAY_MONDAY = 1
    DAY_TUESDAY = 2
    DAY_WEDNESDAY = 3
    DAY_THURSDAY = 4
    DAY_FRIDAY = 5
    DAY_SATURDAY = 6
    DAY_SUNDAY = 7

    DAY_CHOICES = [
        (DAY_MONDAY, 'Monday'),
        (DAY_TUESDAY, 'Tuesday'),
        (DAY_WEDNESDAY, 'Wednesday'),
        (DAY_THURSDAY, 'Thursday'),
        (DAY_FRIDAY, 'Friday'),
        (DAY_SATURDAY, 'Saturday'),
        (DAY_SUNDAY, 'Sunday'),
    ]

    course_name = models.CharField(max_length=120)
    day_of_week = models.PositiveSmallIntegerField(choices=DAY_CHOICES)
    start_time = models.TimeField()
    end_time = models.TimeField()
    classroom = models.CharField(max_length=120)
    created_at = models.DateTimeField(auto_now_add=True)
    user = models.ForeignKey(
        Student,
        on_delete=models.CASCADE,
        related_name='timetable_entries',
    )

    class Meta:
        ordering = ['day_of_week', 'start_time', 'course_name']

    def __str__(self):
        return f'{self.course_name} ({self.get_day_of_week_display()})'
