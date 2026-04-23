import hashlib
import secrets
from datetime import timedelta

from django.db import models
from django.db import transaction
from django.utils import timezone

from .league_services import award_student_xp
from .models import ConnectionRequest, Encounter, Friendship, SocialToken, Student

SOCIAL_TOKEN_TTL_SECONDS = 120
SOCIAL_XP_REWARD = 5
SOCIAL_DAILY_XP_CAP = 5
SOCIAL_SERVICE_UUID = '8d0c5a5e-4e7b-4c2f-8c0d-3d5d89d8f321'


def hash_social_token(raw_token):
    return hashlib.sha256(raw_token.encode('utf-8')).hexdigest()


def issue_social_token(student):
    SocialToken.objects.filter(student=student, is_active=True).update(is_active=False)

    raw_token = secrets.token_hex(8)
    token = SocialToken.objects.create(
        student=student,
        token_hash=hash_social_token(raw_token),
        expires_at=timezone.now() + timedelta(seconds=SOCIAL_TOKEN_TTL_SECONDS),
    )
    return raw_token, token


def deactivate_social_tokens(student):
    return SocialToken.objects.filter(student=student, is_active=True).update(is_active=False)


def resolve_social_token(raw_token):
    token_hash = hash_social_token(raw_token)
    token = (
        SocialToken.objects.select_related('student')
        .filter(token_hash=token_hash, is_active=True)
        .first()
    )
    if token is None:
        return None, 'invalid'

    if token.expires_at <= timezone.now():
        token.is_active = False
        token.save(update_fields=['is_active'])
        return None, 'expired'

    if not token.student.social_discoverable:
        return None, 'hidden'

    return token, None


def get_canonical_friendship_pair(student_one, student_two):
    if student_one.id <= student_two.id:
        return student_one, student_two
    return student_two, student_one


def friendship_exists(student_one, student_two):
    student_a, student_b = get_canonical_friendship_pair(student_one, student_two)
    return Friendship.objects.filter(student_a=student_a, student_b=student_b).exists()


def has_recent_social_reward(student_one, student_two):
    today = timezone.localdate()
    student_ids = {student_one.id, student_two.id}
    recent_encounters = Encounter.objects.filter(
        confirmed=True,
        xp_awarded=True,
        created_at__date=today,
    ).select_related('initiator', 'target')

    for encounter in recent_encounters:
        if {encounter.initiator_id, encounter.target_id} == student_ids:
            return True
    return False


def social_rewards_awarded_today(student, target_date=None):
    reward_date = target_date or timezone.localdate()
    return Encounter.objects.filter(
        confirmed=True,
        xp_awarded=True,
        created_at__date=reward_date,
    ).filter(
        models.Q(initiator=student) | models.Q(target=student)
    ).count()


@transaction.atomic
def confirm_connection_request(connection_request):
    if connection_request.status != ConnectionRequest.STATUS_PENDING:
        return None, False

    connection_request.status = ConnectionRequest.STATUS_ACCEPTED
    connection_request.responded_at = timezone.now()
    connection_request.save(update_fields=['status', 'responded_at'])

    encounter = connection_request.encounters.order_by('-created_at').first()
    if encounter is None:
        encounter = Encounter.objects.create(
            initiator=connection_request.from_student,
            target=connection_request.to_student,
            connection_request=connection_request,
        )

    encounter.confirmed = True
    encounter.confirmed_at = timezone.now()

    pair_reward_already_given = has_recent_social_reward(
        connection_request.from_student,
        connection_request.to_student,
    )
    sender_xp_awarded = False
    recipient_xp_awarded = False

    if not pair_reward_already_given:
        sender_rewards_today = social_rewards_awarded_today(connection_request.from_student)
        recipient_rewards_today = social_rewards_awarded_today(connection_request.to_student)

        if sender_rewards_today < SOCIAL_DAILY_XP_CAP:
            award_student_xp(connection_request.from_student, SOCIAL_XP_REWARD)
            sender_xp_awarded = True

        if recipient_rewards_today < SOCIAL_DAILY_XP_CAP:
            award_student_xp(connection_request.to_student, SOCIAL_XP_REWARD)
            recipient_xp_awarded = True

        encounter.xp_awarded = sender_xp_awarded or recipient_xp_awarded

    encounter.save(update_fields=['confirmed', 'confirmed_at', 'xp_awarded'])
    return encounter, {
        'sender_xp_awarded': sender_xp_awarded,
        'recipient_xp_awarded': recipient_xp_awarded,
        'pair_reward_already_given': pair_reward_already_given,
    }
