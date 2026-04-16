from django.contrib import admin

from .models import (
    ConnectionRequest,
    Encounter,
    Friendship,
    League,
    LeagueMembership,
    LeagueWeek,
    Note,
    SocialToken,
    Task,
    TimetableEntry,
    WeeklyLeagueEntry,
)


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


@admin.register(Note)
class NoteAdmin(admin.ModelAdmin):
    list_display = ('title', 'user', 'note_type', 'course_name', 'linked_task', 'linked_timetable_entry', 'updated_at')
    list_filter = ('note_type', 'user')
    search_fields = ('title', 'content_markdown', 'course_name', 'user__username')


@admin.register(SocialToken)
class SocialTokenAdmin(admin.ModelAdmin):
    list_display = ('student', 'is_active', 'expires_at', 'created_at')
    list_filter = ('is_active',)
    search_fields = ('student__username', 'token_hash')


@admin.register(ConnectionRequest)
class ConnectionRequestAdmin(admin.ModelAdmin):
    list_display = ('from_student', 'to_student', 'status', 'created_at', 'responded_at')
    list_filter = ('status',)
    search_fields = ('from_student__username', 'to_student__username')


@admin.register(Encounter)
class EncounterAdmin(admin.ModelAdmin):
    list_display = ('initiator', 'target', 'confirmed', 'xp_awarded', 'rssi', 'created_at')
    list_filter = ('confirmed', 'xp_awarded')
    search_fields = ('initiator__username', 'target__username')


@admin.register(Friendship)
class FriendshipAdmin(admin.ModelAdmin):
    list_display = ('student_a', 'student_b', 'created_at')
    search_fields = ('student_a__username', 'student_b__username')
