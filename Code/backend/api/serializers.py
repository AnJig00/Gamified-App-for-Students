from rest_framework import serializers

from .league_services import ensure_league_membership, ensure_weekly_entry, get_current_league_week
from .models import Student, Task, TimetableEntry


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
