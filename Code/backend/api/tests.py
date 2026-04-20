from datetime import time
from unittest.mock import patch

from django.urls import reverse
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework import status
from rest_framework.test import APITestCase

from .league_services import award_student_xp, get_current_league_week, settle_league_week
from .models import ConnectionRequest, Encounter, Friendship, LeagueMembership, Note, SocialToken, Student, Task, TimetableEntry


class TaskApiTests(APITestCase):
    def setUp(self):
        self.student = Student.objects.create_user(
            username='tasks_alice',
            email='tasks_alice@example.com',
            password='password123',
        )
        self.other_student = Student.objects.create_user(
            username='tasks_bob',
            email='tasks_bob@example.com',
            password='password123',
        )

    def test_delete_task_removes_it_from_database(self):
        task = Task.objects.create(
            user=self.student,
            title='Temporary reminder',
            is_completed=False,
        )

        response = self.client.delete(
            f"{reverse('task-detail', args=[task.id])}?user_id={self.student.id}",
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertFalse(Task.objects.filter(id=task.id).exists())

    def test_delete_task_rejects_other_users_item(self):
        task = Task.objects.create(
            user=self.other_student,
            title='Private task',
            is_completed=False,
        )

        response = self.client.delete(
            f"{reverse('task-detail', args=[task.id])}?user_id={self.student.id}",
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertTrue(Task.objects.filter(id=task.id).exists())


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


class NoteApiTests(APITestCase):
    def setUp(self):
        self.student = Student.objects.create_user(
            username='notes_alice',
            email='notes_alice@example.com',
            password='password123',
        )
        self.task = Task.objects.create(
            user=self.student,
            title='Finish essay',
            is_completed=False,
        )
        self.timetable_entry = TimetableEntry.objects.create(
            user=self.student,
            course_name='History',
            day_of_week=1,
            start_time=time(9, 0),
            end_time=time(10, 0),
            classroom='Room 202',
        )

    def test_create_task_linked_note(self):
        response = self.client.post(
            f"{reverse('note-list-create')}?user_id={self.student.id}",
            {
                "title": "Essay checklist",
                "content_markdown": "- [ ] Draft intro",
                "note_type": "task",
                "linked_task": self.task.id,
            },
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(Note.objects.count(), 1)
        self.assertEqual(Note.objects.get().linked_task, self.task)

    def test_filter_notes_by_timetable_entry(self):
        Note.objects.create(
            user=self.student,
            title='Lecture recap',
            content_markdown='# Notes',
            note_type='class',
            linked_timetable_entry=self.timetable_entry,
            course_name='History',
        )
        Note.objects.create(
            user=self.student,
            title='Quick thought',
            content_markdown='Remember to revise',
            note_type='quick',
        )

        response = self.client.get(
            f"{reverse('note-list-create')}?user_id={self.student.id}&linked_timetable_entry_id={self.timetable_entry.id}",
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['title'], 'Lecture recap')


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


class SocialApiTests(APITestCase):
    def setUp(self):
        self.alice = Student.objects.create_user(
            username='social_alice',
            email='social_alice@example.com',
            password='password123',
            department='Computer Science',
            year_of_study=2,
        )
        self.bob = Student.objects.create_user(
            username='social_bob',
            email='social_bob@example.com',
            password='password123',
            department='Design',
            year_of_study=3,
        )

    def test_start_presence_and_resolve_token(self):
        start_response = self.client.post(
            f"{reverse('social-presence-start')}?user_id={self.alice.id}",
            format='json',
        )

        self.assertEqual(start_response.status_code, status.HTTP_201_CREATED)
        self.assertIn('token', start_response.data)
        self.assertEqual(SocialToken.objects.filter(student=self.alice, is_active=True).count(), 1)

        resolve_response = self.client.post(
            reverse('social-resolve'),
            {'token': start_response.data['token']},
            format='json',
        )

        self.assertEqual(resolve_response.status_code, status.HTTP_200_OK)
        self.assertEqual(resolve_response.data['student_id'], self.alice.id)
        self.assertEqual(resolve_response.data['username'], 'social_alice')
        self.assertEqual(resolve_response.data['department'], 'Computer Science')
        self.assertEqual(resolve_response.data['year_of_study'], 2)

    def test_stop_presence_deactivates_active_tokens(self):
        self.client.post(
            f"{reverse('social-presence-start')}?user_id={self.alice.id}",
            format='json',
        )

        stop_response = self.client.post(
            f"{reverse('social-presence-stop')}?user_id={self.alice.id}",
            format='json',
        )

        self.assertEqual(stop_response.status_code, status.HTTP_200_OK)
        self.assertEqual(stop_response.data['deactivated_tokens'], 1)
        self.assertFalse(SocialToken.objects.filter(student=self.alice, is_active=True).exists())

    def test_accept_connection_request_confirms_encounter_and_awards_xp(self):
        connect_response = self.client.post(
            f"{reverse('social-connect')}?user_id={self.alice.id}",
            {
                'target_student_id': self.bob.id,
                'rssi': -52,
            },
            format='json',
        )

        self.assertEqual(connect_response.status_code, status.HTTP_201_CREATED)
        request_id = connect_response.data['request']['id']

        confirm_response = self.client.post(
            f"{reverse('social-confirm')}?user_id={self.bob.id}",
            {
                'request_id': request_id,
                'action': 'accept',
            },
            format='json',
        )

        self.assertEqual(confirm_response.status_code, status.HTTP_200_OK)
        self.assertTrue(confirm_response.data['xp_awarded'])

        connection_request = ConnectionRequest.objects.get(pk=request_id)
        encounter = Encounter.objects.get(connection_request=connection_request)
        self.alice.refresh_from_db()
        self.bob.refresh_from_db()

        self.assertEqual(connection_request.status, ConnectionRequest.STATUS_ACCEPTED)
        self.assertTrue(encounter.confirmed)
        self.assertTrue(encounter.xp_awarded)
        self.assertEqual(self.alice.current_xp, 5)
        self.assertEqual(self.bob.current_xp, 5)

    def test_can_add_friend_after_request_is_accepted(self):
        connect_response = self.client.post(
            f"{reverse('social-connect')}?user_id={self.alice.id}",
            {'target_student_id': self.bob.id},
            format='json',
        )
        request_id = connect_response.data['request']['id']

        self.client.post(
            f"{reverse('social-confirm')}?user_id={self.bob.id}",
            {
                'request_id': request_id,
                'action': 'accept',
            },
            format='json',
        )

        friend_response = self.client.post(
            f"{reverse('social-add-friend')}?user_id={self.alice.id}",
            {'request_id': request_id},
            format='json',
        )

        self.assertEqual(friend_response.status_code, status.HTTP_201_CREATED)
        self.assertTrue(Friendship.objects.exists())

    def test_reject_connection_request_marks_request_rejected(self):
        connect_response = self.client.post(
            f"{reverse('social-connect')}?user_id={self.alice.id}",
            {'target_student_id': self.bob.id},
            format='json',
        )
        request_id = connect_response.data['request']['id']

        reject_response = self.client.post(
            f"{reverse('social-confirm')}?user_id={self.bob.id}",
            {
                'request_id': request_id,
                'action': 'reject',
            },
            format='json',
        )

        self.assertEqual(reject_response.status_code, status.HTTP_200_OK)
        self.assertEqual(reject_response.data['request']['status'], ConnectionRequest.STATUS_REJECTED)
        self.assertFalse(Encounter.objects.filter(connection_request_id=request_id, confirmed=True).exists())

    def test_friend_list_returns_counterpart_profile_details(self):
        connect_response = self.client.post(
            f"{reverse('social-connect')}?user_id={self.alice.id}",
            {'target_student_id': self.bob.id},
            format='json',
        )
        request_id = connect_response.data['request']['id']

        self.client.post(
            f"{reverse('social-confirm')}?user_id={self.bob.id}",
            {
                'request_id': request_id,
                'action': 'accept',
            },
            format='json',
        )
        self.client.post(
            f"{reverse('social-add-friend')}?user_id={self.alice.id}",
            {'request_id': request_id},
            format='json',
        )

        friend_list_response = self.client.get(
            f"{reverse('social-add-friend')}?user_id={self.alice.id}",
            format='json',
        )

        self.assertEqual(friend_list_response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(friend_list_response.data['friends']), 1)
        self.assertEqual(friend_list_response.data['friends'][0]['friend_id'], self.bob.id)
        self.assertEqual(friend_list_response.data['friends'][0]['username'], 'social_bob')
        self.assertEqual(friend_list_response.data['friends'][0]['department'], 'Design')
        self.assertEqual(friend_list_response.data['friends'][0]['year_of_study'], 3)

    def test_duplicate_pending_request_returns_existing_request(self):
        first_response = self.client.post(
            f"{reverse('social-connect')}?user_id={self.alice.id}",
            {'target_student_id': self.bob.id},
            format='json',
        )
        second_response = self.client.post(
            f"{reverse('social-connect')}?user_id={self.alice.id}",
            {'target_student_id': self.bob.id},
            format='json',
        )

        self.assertEqual(first_response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(second_response.status_code, status.HTTP_200_OK)
        self.assertEqual(ConnectionRequest.objects.count(), 1)


class AvatarApiTests(APITestCase):
    def setUp(self):
        self.student = Student.objects.create_user(
            username='avatar_alice',
            email='avatar_alice@example.com',
            password='password123',
        )

    @patch('api.views.upload_avatar_to_cloudinary')
    def test_profile_returns_avatar_url_after_upload(self, mocked_upload):
        mocked_upload.return_value = 'https://res.cloudinary.com/demo/image/upload/v1/meet-merit/avatars/student-1.png'

        avatar_file = SimpleUploadedFile(
            'avatar.png',
            b'\x89PNG\r\n\x1a\navatar-bytes',
            content_type='image/png',
        )

        upload_response = self.client.post(
            f"{reverse('profile-avatar-upload')}?user_id={self.student.id}",
            {'avatar': avatar_file},
            format='multipart',
        )

        self.assertEqual(upload_response.status_code, status.HTTP_200_OK)
        self.assertEqual(upload_response.data['avatar_url'], mocked_upload.return_value)

        profile_response = self.client.get(
            f"{reverse('profile')}?user_id={self.student.id}",
            format='json',
        )

        self.assertEqual(profile_response.status_code, status.HTTP_200_OK)
        self.assertEqual(profile_response.data['avatar_url'], upload_response.data['avatar_url'])

    def test_avatar_upload_rejects_non_image_files(self):
        avatar_file = SimpleUploadedFile(
            'avatar.txt',
            b'not-an-image',
            content_type='text/plain',
        )

        upload_response = self.client.post(
            f"{reverse('profile-avatar-upload')}?user_id={self.student.id}",
            {'avatar': avatar_file},
            format='multipart',
        )

        self.assertEqual(upload_response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('avatar', upload_response.data)
