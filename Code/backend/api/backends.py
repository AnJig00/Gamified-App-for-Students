from django.contrib.auth.backends import ModelBackend
from django.db.models import Q
from .models import Student


class EmailOrUsernameBackend(ModelBackend):
    """Allow authentication with either username or email."""

    def authenticate(self, request, username=None, password=None, **kwargs):
        if username is None or password is None:
            return None

        try:
            user = Student.objects.get(Q(username=username) | Q(email=username))
        except (Student.DoesNotExist, Student.MultipleObjectsReturned):
            return None

        if user.check_password(password) and self.user_can_authenticate(user):
            return user
        return None
