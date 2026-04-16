from rest_framework import serializers

from .avatar_utils import get_avatar_url
from .league_services import ensure_league_membership, ensure_weekly_entry, get_current_league_week
from .models import ConnectionRequest, Encounter, Friendship, Note, Student, Task, TimetableEntry


class StudentSerializer(serializers.ModelSerializer):
    class Meta:
        model = Student
        fields = [
            'id',
            'username',
            'email',
            'password',
            'department',
            'year_of_study',
            'social_discoverable',
        ]
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


class SocialPresenceStartResponseSerializer(serializers.Serializer):
    token = serializers.CharField()
    expires_at = serializers.DateTimeField()
    ttl_seconds = serializers.IntegerField()
    service_uuid = serializers.CharField()


class SocialResolveRequestSerializer(serializers.Serializer):
    token = serializers.CharField(max_length=64)


class SocialResolveResponseSerializer(serializers.Serializer):
    student_id = serializers.IntegerField()
    username = serializers.CharField()
    avatar_url = serializers.CharField(allow_null=True, required=False)
    department = serializers.CharField(allow_blank=True)
    year_of_study = serializers.IntegerField(allow_null=True)


class SocialConnectRequestSerializer(serializers.Serializer):
    target_student_id = serializers.IntegerField()
    rssi = serializers.IntegerField(required=False, allow_null=True)


class SocialRespondRequestSerializer(serializers.Serializer):
    request_id = serializers.IntegerField()
    action = serializers.ChoiceField(choices=['accept', 'reject'])


class SocialFriendshipRequestSerializer(serializers.Serializer):
    request_id = serializers.IntegerField()


class ConnectionRequestSerializer(serializers.ModelSerializer):
    from_username = serializers.CharField(source='from_student.username', read_only=True)
    from_avatar_url = serializers.SerializerMethodField()
    from_department = serializers.CharField(source='from_student.department', read_only=True)
    from_year_of_study = serializers.IntegerField(
        source='from_student.year_of_study',
        read_only=True,
        allow_null=True,
    )
    to_username = serializers.CharField(source='to_student.username', read_only=True)
    to_avatar_url = serializers.SerializerMethodField()
    to_department = serializers.CharField(source='to_student.department', read_only=True)
    to_year_of_study = serializers.IntegerField(
        source='to_student.year_of_study',
        read_only=True,
        allow_null=True,
    )

    class Meta:
        model = ConnectionRequest
        fields = [
            'id',
            'from_student',
            'from_username',
            'from_avatar_url',
            'from_department',
            'from_year_of_study',
            'to_student',
            'to_username',
            'to_avatar_url',
            'to_department',
            'to_year_of_study',
            'status',
            'created_at',
            'responded_at',
        ]

    def get_from_avatar_url(self, obj):
        return get_avatar_url(obj.from_student, self.context.get('request'))

    def get_to_avatar_url(self, obj):
        return get_avatar_url(obj.to_student, self.context.get('request'))


class EncounterSerializer(serializers.ModelSerializer):
    initiator_username = serializers.CharField(source='initiator.username', read_only=True)
    target_username = serializers.CharField(source='target.username', read_only=True)

    class Meta:
        model = Encounter
        fields = [
            'id',
            'initiator',
            'initiator_username',
            'target',
            'target_username',
            'connection_request',
            'rssi',
            'confirmed',
            'confirmed_at',
            'xp_awarded',
            'created_at',
        ]


class FriendshipSerializer(serializers.ModelSerializer):
    student_a_username = serializers.CharField(source='student_a.username', read_only=True)
    student_b_username = serializers.CharField(source='student_b.username', read_only=True)

    class Meta:
        model = Friendship
        fields = [
            'id',
            'student_a',
            'student_a_username',
            'student_b',
            'student_b_username',
            'created_at',
        ]


class SocialFriendSummarySerializer(serializers.Serializer):
    id = serializers.IntegerField()
    friend_id = serializers.IntegerField()
    username = serializers.CharField()
    avatar_url = serializers.CharField(allow_null=True, required=False)
    department = serializers.CharField(allow_blank=True)
    year_of_study = serializers.IntegerField(allow_null=True)
    created_at = serializers.DateTimeField()
