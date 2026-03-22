from django.contrib import admin

from .models import League, LeagueMembership, LeagueWeek, Task, TimetableEntry, WeeklyLeagueEntry


@admin.register(Task)
class TaskAdmin(admin.ModelAdmin):
    list_display = ('title', 'user', 'is_completed', 'created_at')
    list_filter = ('is_completed', 'user')
    search_fields = ('title', 'user__username')


@admin.register(TimetableEntry)
class TimetableEntryAdmin(admin.ModelAdmin):
    list_display = ('course_name', 'user', 'day_of_week', 'start_time', 'end_time', 'classroom')
    list_filter = ('day_of_week', 'user')
    search_fields = ('course_name', 'classroom', 'user__username')


@admin.register(League)
class LeagueAdmin(admin.ModelAdmin):
    list_display = ('name', 'code', 'tier', 'promotion_slots', 'relegation_slots')
    ordering = ('tier',)


@admin.register(LeagueWeek)
class LeagueWeekAdmin(admin.ModelAdmin):
    list_display = ('start_date', 'end_date', 'is_settled', 'settled_at')
    list_filter = ('is_settled',)
    ordering = ('-start_date',)


@admin.register(LeagueMembership)
class LeagueMembershipAdmin(admin.ModelAdmin):
    list_display = ('student', 'league', 'last_outcome', 'last_transition_at', 'last_settled_week')
    list_filter = ('league', 'last_outcome')
    search_fields = ('student__username',)


@admin.register(WeeklyLeagueEntry)
class WeeklyLeagueEntryAdmin(admin.ModelAdmin):
    list_display = ('student', 'league', 'week', 'weekly_xp', 'final_rank', 'outcome')
    list_filter = ('league', 'week', 'outcome')
    search_fields = ('student__username',)
