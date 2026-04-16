from pathlib import Path

from rest_framework.exceptions import ValidationError

MAX_AVATAR_BYTES = 2 * 1024 * 1024
ALLOWED_AVATAR_EXTENSIONS = {'.jpg', '.jpeg', '.png', '.webp'}


def get_avatar_url(student, request=None):
    if not getattr(student, 'avatar', None):
        return None
    try:
        avatar_url = student.avatar.url
    except ValueError:
        return None
    if request is not None:
        return request.build_absolute_uri(avatar_url)
    return avatar_url


def validate_avatar_upload(avatar_file):
    if avatar_file is None:
        raise ValidationError({'avatar': 'Please choose an image to upload.'})

    extension = Path(avatar_file.name or '').suffix.lower()
    if extension not in ALLOWED_AVATAR_EXTENSIONS:
        raise ValidationError({'avatar': 'Use JPG, PNG, or WEBP images only.'})

    content_type = getattr(avatar_file, 'content_type', '')
    if content_type and not content_type.startswith('image/'):
        raise ValidationError({'avatar': 'The selected file is not an image.'})

    if avatar_file.size > MAX_AVATAR_BYTES:
        raise ValidationError({'avatar': 'Avatar images must be 2 MB or smaller.'})
