from datetime import timedelta

from django.db import transaction
from django.db.models import F
from django.utils import timezone

from .models import League, LeagueMembership, LeagueWeek, Student, WeeklyLeagueEntry


DEFAULT_LEAGUES = [
    {'code': 'bronze', 'name': 'Bronze League', 'tier': 1},
    {'code': 'silver', 'name': 'Silver League', 'tier': 2},
    {'code': 'gold', 'name': 'Gold League', 'tier': 3},
    {'code': 'diamond', 'name': 'Diamond League', 'tier': 4},
]


def bootstrap_league_system():
    for definition in DEFAULT_LEAGUES:
        League.objects.update_or_create(
            code=definition['code'],
            defaults=definition,
        )


def get_week_bounds(target_date=None):
    target_date = target_date or timezone.localdate()
    start_date = target_date - timedelta(days=target_date.weekday())
    end_date = start_date + timedelta(days=6)
    return start_date, end_date


def get_current_league_week(target_date=None):
    bootstrap_league_system()
    start_date, end_date = get_week_bounds(target_date)
    week, _ = LeagueWeek.objects.get_or_create(
        start_date=start_date,
        defaults={'end_date': end_date},
    )
    if week.end_date != end_date:
        week.end_date = end_date
        week.save(update_fields=['end_date'])
    return week


def get_default_league():
    bootstrap_league_system()
    return League.objects.order_by('tier').first()


def get_adjacent_league(league, direction):
    tier_delta = 1 if direction == 'up' else -1
    target_tier = league.tier + tier_delta
    return League.objects.filter(tier=target_tier).first() or league


def ensure_league_membership(student):
    membership = getattr(student, 'league_membership', None)
    if membership is not None:
        return membership

    membership, _ = LeagueMembership.objects.get_or_create(
        student=student,
        defaults={
            'league': get_default_league(),
            'last_outcome': LeagueMembership.OUTCOME_PENDING,
        },
    )
    return membership


def ensure_weekly_entry(student, week=None):
    week = week or get_current_league_week()
    membership = ensure_league_membership(student)
    entry, created = WeeklyLeagueEntry.objects.get_or_create(
        week=week,
        student=student,
        defaults={'league': membership.league},
    )
    if not created and entry.league_id != membership.league_id and entry.weekly_xp == 0:
        entry.league = membership.league
        entry.save(update_fields=['league'])
    return entry


@transaction.atomic
def award_student_xp(student, xp_amount):
    week = get_current_league_week()
    entry = ensure_weekly_entry(student, week)

    Student.objects.filter(pk=student.pk).update(current_xp=F('current_xp') + xp_amount)
    WeeklyLeagueEntry.objects.filter(pk=entry.pk).update(weekly_xp=F('weekly_xp') + xp_amount)

    student.refresh_from_db(fields=['current_xp'])
    entry.refresh_from_db(fields=['weekly_xp'])
    return entry


def get_ranked_entries_for_week(week, league):
    entries = list(
        WeeklyLeagueEntry.objects.filter(week=week, league=league)
        .select_related('student', 'league')
        .order_by('-weekly_xp', '-student__current_xp', 'student__username')
    )
    for index, entry in enumerate(entries, start=1):
        entry.computed_rank = index
    return entries


def _build_status(student, week, membership, entries):
    entry = next((item for item in entries if item.student_id == student.id), None)
    promotion_slots = membership.league.promotion_slots
    relegation_slots = membership.league.relegation_slots
    participant_count = len(entries)

    rank = getattr(entry, 'computed_rank', None) if entry else None
    weekly_xp = entry.weekly_xp if entry else 0

    promotion_entry = entries[promotion_slots - 1] if participant_count >= promotion_slots else None
    points_to_promotion = None
    if rank is not None and rank > promotion_slots and promotion_entry is not None:
        points_to_promotion = max(promotion_entry.weekly_xp - weekly_xp + 1, 1)
    elif rank is not None and rank <= promotion_slots:
        points_to_promotion = 0

    relegation_cutoff_rank = None
    points_above_relegation = None
    if relegation_slots > 0 and participant_count > relegation_slots:
        relegation_cutoff_rank = participant_count - relegation_slots + 1
        if rank is not None:
            safe_entries = entries[:relegation_cutoff_rank - 1]
            if rank >= relegation_cutoff_rank:
                if relegation_cutoff_rank > 1:
                    safe_xp = entries[relegation_cutoff_rank - 2].weekly_xp
                    points_above_relegation = max(safe_xp - weekly_xp + 1, 1)
                else:
                    points_above_relegation = 0
            else:
                danger_xp = entries[relegation_cutoff_rank - 1].weekly_xp
                points_above_relegation = max(weekly_xp - danger_xp, 0)

    last_outcome_label = {
        LeagueMembership.OUTCOME_PROMOTED: 'Promoted last week',
        LeagueMembership.OUTCOME_RELEGATED: 'Relegated last week',
        LeagueMembership.OUTCOME_STAYED: 'Held your league last week',
        LeagueMembership.OUTCOME_PENDING: 'Your first league week is underway',
    }[membership.last_outcome]

    return {
        'league_code': membership.league.code,
        'league_name': membership.league.name,
        'league_tier': membership.league.tier,
        'is_top_league': not League.objects.filter(tier__gt=membership.league.tier).exists(),
        'is_bottom_league': not League.objects.filter(tier__lt=membership.league.tier).exists(),
        'week_start': week.start_date,
        'week_end': week.end_date,
        'week_label': f'{week.start_date:%b %d} - {week.end_date:%b %d}',
        'weekly_xp': weekly_xp,
        'rank': rank,
        'participants': participant_count,
        'promotion_slots': promotion_slots,
        'relegation_slots': relegation_slots,
        'promotion_cutoff_rank': promotion_slots if participant_count >= promotion_slots else None,
        'relegation_cutoff_rank': relegation_cutoff_rank,
        'points_to_promotion': points_to_promotion,
        'points_above_relegation': points_above_relegation,
        'last_outcome': membership.last_outcome,
        'last_outcome_label': last_outcome_label,
    }


def get_league_status_for_student(student, target_date=None):
    week = get_current_league_week(target_date)
    membership = ensure_league_membership(student)
    ensure_weekly_entry(student, week)
    entries = get_ranked_entries_for_week(week, membership.league)
    return _build_status(student, week, membership, entries)


def get_league_leaderboard_for_student(student, target_date=None):
    week = get_current_league_week(target_date)
    membership = ensure_league_membership(student)
    ensure_weekly_entry(student, week)
    entries = get_ranked_entries_for_week(week, membership.league)

    return {
        'league_code': membership.league.code,
        'league_name': membership.league.name,
        'week_start': week.start_date,
        'week_end': week.end_date,
        'week_label': f'{week.start_date:%b %d} - {week.end_date:%b %d}',
        'participants': len(entries),
        'promotion_slots': membership.league.promotion_slots,
        'relegation_slots': membership.league.relegation_slots,
        'entries': [
            {
                'rank': entry.computed_rank,
                'username': entry.student.username,
                'weekly_xp': entry.weekly_xp,
                'level': entry.student.level,
                'is_current_user': entry.student_id == student.id,
            }
            for entry in entries
        ],
    }


@transaction.atomic
def settle_league_week(week):
    if week.is_settled:
        return week

    bootstrap_league_system()
    leagues = list(League.objects.order_by('tier'))

    for league in leagues:
        entries = get_ranked_entries_for_week(week, league)
        participant_count = len(entries)
        if participant_count == 0:
            continue

        promotion_limit = min(league.promotion_slots, participant_count)
        relegation_start = max(participant_count - league.relegation_slots + 1, 1)

        for entry in entries:
            rank = entry.computed_rank
            target_league = league
            outcome = WeeklyLeagueEntry.OUTCOME_STAYED

            if rank <= promotion_limit:
                next_league = get_adjacent_league(league, 'up')
                if next_league.id != league.id:
                    target_league = next_league
                    outcome = WeeklyLeagueEntry.OUTCOME_PROMOTED
            elif rank >= relegation_start:
                previous_league = get_adjacent_league(league, 'down')
                if previous_league.id != league.id:
                    target_league = previous_league
                    outcome = WeeklyLeagueEntry.OUTCOME_RELEGATED

            WeeklyLeagueEntry.objects.filter(pk=entry.pk).update(
                final_rank=rank,
                outcome=outcome,
            )
            LeagueMembership.objects.update_or_create(
                student=entry.student,
                defaults={
                    'league': target_league,
                    'last_outcome': outcome,
                    'last_transition_at': timezone.now(),
                    'last_settled_week': week,
                },
            )

    week.is_settled = True
    week.settled_at = timezone.now()
    week.save(update_fields=['is_settled', 'settled_at'])
    return week


def prime_current_week_for_all_students(target_date=None):
    week = get_current_league_week(target_date)
    for student in Student.objects.all():
        ensure_weekly_entry(student, week)
    return week


def get_latest_completed_unsettled_week(target_date=None):
    target_date = target_date or timezone.localdate()
    return (
        LeagueWeek.objects.filter(is_settled=False, end_date__lt=target_date)
        .order_by('-start_date')
        .first()
    )
