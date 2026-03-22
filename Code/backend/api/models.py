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


class League(models.Model):
    code = models.SlugField(unique=True)
    name = models.CharField(max_length=80)
    tier = models.PositiveSmallIntegerField(unique=True)
    promotion_slots = models.PositiveSmallIntegerField(default=5)
    relegation_slots = models.PositiveSmallIntegerField(default=5)

    class Meta:
        ordering = ['tier']

    def __str__(self):
        return self.name


class LeagueWeek(models.Model):
    start_date = models.DateField(unique=True)
    end_date = models.DateField()
    is_settled = models.BooleanField(default=False)
    settled_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ['-start_date']

    def __str__(self):
        return f'{self.start_date} to {self.end_date}'


class LeagueMembership(models.Model):
    OUTCOME_PENDING = 'PENDING'
    OUTCOME_PROMOTED = 'PROMOTED'
    OUTCOME_STAYED = 'STAYED'
    OUTCOME_RELEGATED = 'RELEGATED'

    OUTCOME_CHOICES = [
        (OUTCOME_PENDING, 'Pending'),
        (OUTCOME_PROMOTED, 'Promoted'),
        (OUTCOME_STAYED, 'Stayed'),
        (OUTCOME_RELEGATED, 'Relegated'),
    ]

    student = models.OneToOneField(
        Student,
        on_delete=models.CASCADE,
        related_name='league_membership',
    )
    league = models.ForeignKey(
        League,
        on_delete=models.CASCADE,
        related_name='memberships',
    )
    last_outcome = models.CharField(
        max_length=16,
        choices=OUTCOME_CHOICES,
        default=OUTCOME_PENDING,
    )
    last_transition_at = models.DateTimeField(null=True, blank=True)
    last_settled_week = models.ForeignKey(
        LeagueWeek,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name='updated_memberships',
    )
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['league__tier', 'student__username']

    def __str__(self):
        return f'{self.student.username} in {self.league.name}'


class WeeklyLeagueEntry(models.Model):
    OUTCOME_PENDING = 'PENDING'
    OUTCOME_PROMOTED = 'PROMOTED'
    OUTCOME_STAYED = 'STAYED'
    OUTCOME_RELEGATED = 'RELEGATED'

    OUTCOME_CHOICES = [
        (OUTCOME_PENDING, 'Pending'),
        (OUTCOME_PROMOTED, 'Promoted'),
        (OUTCOME_STAYED, 'Stayed'),
        (OUTCOME_RELEGATED, 'Relegated'),
    ]

    week = models.ForeignKey(
        LeagueWeek,
        on_delete=models.CASCADE,
        related_name='entries',
    )
    student = models.ForeignKey(
        Student,
        on_delete=models.CASCADE,
        related_name='weekly_league_entries',
    )
    league = models.ForeignKey(
        League,
        on_delete=models.CASCADE,
        related_name='weekly_entries',
    )
    weekly_xp = models.IntegerField(default=0)
    final_rank = models.PositiveIntegerField(null=True, blank=True)
    outcome = models.CharField(
        max_length=16,
        choices=OUTCOME_CHOICES,
        default=OUTCOME_PENDING,
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['league__tier', '-weekly_xp', 'student__username']
        constraints = [
            models.UniqueConstraint(
                fields=['week', 'student'],
                name='unique_weekly_entry_per_student',
            ),
        ]

    def __str__(self):
        return f'{self.student.username} - {self.league.name} - {self.week.start_date}'
