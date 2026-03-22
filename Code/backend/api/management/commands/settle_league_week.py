from django.core.management.base import BaseCommand, CommandError
from django.utils import timezone

from api.league_services import (
    get_current_league_week,
    get_latest_completed_unsettled_week,
    prime_current_week_for_all_students,
    settle_league_week,
)


class Command(BaseCommand):
    help = 'Settles the latest completed league week and primes the current week.'

    def add_arguments(self, parser):
        parser.add_argument(
            '--include-current',
            action='store_true',
            help='Allow settling the current in-progress week when no completed week is waiting.',
        )

    def handle(self, *args, **options):
        today = timezone.localdate()
        week = get_latest_completed_unsettled_week(today)

        if week is None and options['include_current']:
            week = get_current_league_week(today)

        if week is None:
            raise CommandError('No completed unsettled league week is available to settle.')

        settle_league_week(week)
        current_week = prime_current_week_for_all_students(today)

        self.stdout.write(
            self.style.SUCCESS(
                f'Settled league week {week.start_date} - {week.end_date}. '
                f'Current week is {current_week.start_date} - {current_week.end_date}.'
            )
        )
