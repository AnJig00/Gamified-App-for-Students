from pathlib import Path

from django.conf import settings
from rest_framework.exceptions import ValidationError

MAX_AVATAR_BYTES = 2 * 1024 * 1024
ALLOWED_AVATAR_EXTENSIONS = {'.jpg', '.jpeg', '.png', '.webp'}


def get_avatar_url(student, request=None):
    avatar_value = (getattr(student, 'avatar', '') or '').strip()
    if not avatar_value:
        return None
    if avatar_value.startswith('http://') or avatar_value.startswith('https://'):
        return avatar_value

    media_path = avatar_value if avatar_value.startswith('/') else f"{settings.MEDIA_URL}{avatar_value}"
    if request is not None:
        return request.build_absolute_uri(media_path)
    return media_path


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


def upload_avatar_to_cloudinary(student, avatar_file):
    import os

    cloud_name = os_or_none('CLOUDINARY_CLOUD_NAME')
    api_key = os_or_none('CLOUDINARY_API_KEY')
    api_secret = os_or_none('CLOUDINARY_API_SECRET')
    cloudinary_url = os_or_none('CLOUDINARY_URL')

    if not cloudinary_url and not all([cloud_name, api_key, api_secret]):
        raise ValidationError({'avatar': 'Avatar storage is not configured on the server.'})

    import cloudinary
    import cloudinary.uploader

    if cloudinary_url:
        os.environ['CLOUDINARY_URL'] = cloudinary_url
        cloudinary.config(secure=True)
    else:
        cloudinary.config(
            cloud_name=cloud_name,
            api_key=api_key,
            api_secret=api_secret,
            secure=True,
        )

    folder = os_or_none('CLOUDINARY_AVATAR_FOLDER') or 'meet-merit/avatars'
    upload_result = cloudinary.uploader.upload(
        avatar_file,
        folder=folder,
        public_id=f'student-{student.id}',
        overwrite=True,
        invalidate=True,
        resource_type='image',
    )
    secure_url = upload_result.get('secure_url') or upload_result.get('url')
    if not secure_url:
        raise ValidationError({'avatar': 'Cloud upload succeeded but no avatar URL was returned.'})
    return secure_url


def os_or_none(key):
    value = getattr(settings, key, None)
    if value:
        return value
    import os
    env_value = os.environ.get(key, '').strip()
    return env_value or None
