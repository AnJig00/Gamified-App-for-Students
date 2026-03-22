from rest_framework import serializers

from .league_services import ensure_league_membership, ensure_weekly_entry, get_current_league_week
from .models import Note, Student, Task, TimetableEntry


class StudentSerializer(serializers.ModelSerializer):
    class Meta:
        model = Student
        fields = ['id', 'username', 'email', 'password']
        extra_kwargs = {'password': {'write_only': True}}

    def create(self, validated_data):
        user = Student.objects.create_user(**validated_data)
        ensure_league_membership(user)
        ensure_weekly_entry(user, get_current_league_week())
        return user

class TaskSerializer(serializers.ModelSerializer):
    class Meta:
        model = Task
        fields = ['id', 'title', 'is_completed', 'due_date', 'created_at']
        read_only_fields = ['id', 'created_at']


class TimetableEntrySerializer(serializers.ModelSerializer):
    class Meta:
        model = TimetableEntry
        fields = [
            'id',
            'course_name',
            'day_of_week',
            'start_time',
            'end_time',
            'classroom',
            'created_at',
        ]
        read_only_fields = ['id', 'created_at']

    def validate(self, attrs):
        start_time = attrs.get('start_time', getattr(self.instance, 'start_time', None))
        end_time = attrs.get('end_time', getattr(self.instance, 'end_time', None))

        if start_time and end_time and end_time <= start_time:
            raise serializers.ValidationError('End time must be later than start time.')

        return attrs


class NoteSerializer(serializers.ModelSerializer):
    class Meta:
        model = Note
        fields = [
            'id',
            'title',
            'content_markdown',
            'note_type',
            'course_name',
            'linked_task',
            'linked_timetable_entry',
            'created_at',
            'updated_at',
        ]
        read_only_fields = ['id', 'created_at', 'updated_at']

    def validate(self, attrs):
        instance = getattr(self, 'instance', None)
        user = self.context.get('user')

        linked_task = attrs.get('linked_task', getattr(instance, 'linked_task', None))
        linked_timetable_entry = attrs.get(
            'linked_timetable_entry',
            getattr(instance, 'linked_timetable_entry', None),
        )
        note_type = attrs.get('note_type', getattr(instance, 'note_type', Note.TYPE_QUICK))
        course_name = attrs.get('course_name', getattr(instance, 'course_name', ''))

        if linked_task is not None and linked_task.user_id != user.id:
            raise serializers.ValidationError('You can only link notes to your own tasks.')

        if linked_timetable_entry is not None and linked_timetable_entry.user_id != user.id:
            raise serializers.ValidationError('You can only link notes to your own timetable entries.')

        if note_type == Note.TYPE_TASK and linked_task is None:
            raise serializers.ValidationError('Task notes must be linked to a task.')

        if note_type == Note.TYPE_CLASS and linked_timetable_entry is None:
            raise serializers.ValidationError('Class notes must be linked to a timetable entry.')

        if note_type == Note.TYPE_COURSE and not course_name.strip():
            raise serializers.ValidationError('Course notes require a course name.')

        return attrs
