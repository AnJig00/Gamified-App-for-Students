from datetime import time

from django.urls import reverse
from rest_framework import status
from rest_framework.test import APITestCase

from .league_services import award_student_xp, get_current_league_week, settle_league_week
from .models import LeagueMembership, Student, TimetableEntry


class TimetableApiTests(APITestCase):
    def setUp(self):
        self.student = Student.objects.create_user(
            username='alice',
            email='alice@example.com',
            password='password123',
        )
        self.other_student = Student.objects.create_user(
            username='bob',
            email='bob@example.com',
            password='password123',
        )

    def test_create_timetable_entry(self):
        response = self.client.post(
            f"{reverse('timetable-list-create')}?user_id={self.student.id}",
            {
                "course_name": "Math",
                "day_of_week": 1,
                "start_time": "09:00:00",
                "end_time": "10:00:00",
                "classroom": "A101",
            },
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(TimetableEntry.objects.count(), 1)
        self.assertEqual(TimetableEntry.objects.get().user, self.student)

    def test_rejects_invalid_time_range(self):
        response = self.client.post(
            f"{reverse('timetable-list-create')}?user_id={self.student.id}",
            {
                "course_name": "Physics",
                "day_of_week": 2,
                "start_time": "14:00:00",
                "end_time": "13:30:00",
                "classroom": "B204",
            },
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('non_field_errors', response.data)

    def test_only_returns_current_users_entries(self):
        TimetableEntry.objects.create(
            user=self.student,
            course_name='Chemistry',
            day_of_week=3,
            start_time=time(11, 0),
            end_time=time(12, 0),
            classroom='Lab 1',
        )
        TimetableEntry.objects.create(
            user=self.other_student,
            course_name='Biology',
            day_of_week=4,
            start_time=time(13, 0),
            end_time=time(14, 0),
            classroom='C302',
        )

        response = self.client.get(
            f"{reverse('timetable-list-create')}?user_id={self.student.id}",
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['course_name'], 'Chemistry')


class LeagueApiTests(APITestCase):
    def setUp(self):
        self.student = Student.objects.create_user(
            username='league_alice',
            email='league_alice@example.com',
            password='password123',
        )

    def test_league_status_returns_weekly_progress(self):
        award_student_xp(self.student, 25)

        response = self.client.get(
            f"{reverse('league-status')}?user_id={self.student.id}",
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['league_name'], 'Bronze League')
        self.assertEqual(response.data['weekly_xp'], 25)
        self.assertEqual(response.data['rank'], 1)

    def test_profile_endpoint_returns_growth_snapshot(self):
        award_student_xp(self.student, 130)

        response = self.client.get(
            f"{reverse('profile')}?user_id={self.student.id}",
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['username'], 'league_alice')
        self.assertEqual(response.data['current_xp'], 130)
        self.assertEqual(response.data['level'], 2)
        self.assertEqual(response.data['weekly_xp'], 130)
        self.assertEqual(response.data['league_name'], 'Bronze League')
        self.assertEqual(response.data['xp_into_level'], 30)
        self.assertEqual(response.data['xp_remaining_to_next_level'], 70)

    def test_settlement_promotes_top_five_students(self):
        week = get_current_league_week()
        students = [self.student]
        xp_values = [60, 50, 40, 30, 20, 10]

        for index in range(2, 7):
            students.append(
                Student.objects.create_user(
                    username=f'league_user_{index}',
                    email=f'league_user_{index}@example.com',
                    password='password123',
                )
            )

        for student, xp_amount in zip(students, xp_values):
            award_student_xp(student, xp_amount)

        settle_league_week(week)

        memberships = {
            student.username: LeagueMembership.objects.get(student=student)
            for student in students
        }

        promoted_usernames = {
            username
            for username, membership in memberships.items()
            if membership.league.code == 'silver'
        }
        self.assertEqual(len(promoted_usernames), 5)
        self.assertNotIn('league_user_6', promoted_usernames)
