from datetime import time

from django.urls import reverse
from rest_framework import status
from rest_framework.test import APITestCase

from .models import Student, TimetableEntry


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
