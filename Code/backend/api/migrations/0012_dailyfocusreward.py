from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0011_task_completion_tracking'),
    ]

    operations = [
        migrations.CreateModel(
            name='DailyFocusReward',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('reward_date', models.DateField()),
                ('rewarded_minutes', models.PositiveSmallIntegerField(default=0)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('updated_at', models.DateTimeField(auto_now=True)),
                ('student', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='daily_focus_rewards', to='api.student')),
            ],
            options={
                'ordering': ['-reward_date', 'student__username'],
            },
        ),
        migrations.AddConstraint(
            model_name='dailyfocusreward',
            constraint=models.UniqueConstraint(fields=('student', 'reward_date'), name='unique_daily_focus_reward_per_student'),
        ),
    ]
