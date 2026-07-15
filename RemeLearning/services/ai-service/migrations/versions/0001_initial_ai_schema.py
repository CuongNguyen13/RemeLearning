"""initial ai schema: known_faces, face_recognition_results, voice_authenticity_results

Revision ID: 0001
Revises:
Create Date: 2026-07-15

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects.postgresql import JSONB

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

SCHEMA = "ai"


def upgrade() -> None:
    op.execute(f"CREATE SCHEMA IF NOT EXISTS {SCHEMA}")

    op.create_table(
        "known_faces",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("user_id", sa.String(length=100), nullable=False, unique=True),
        sa.Column("name", sa.String(length=255), nullable=False),
        sa.Column("embedding", JSONB(), nullable=False),
        sa.Column("source", sa.String(length=50), nullable=False, server_default="user-service-photo"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        schema=SCHEMA,
    )

    op.create_table(
        "face_recognition_results",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("recording_id", sa.String(length=100), nullable=False),
        sa.Column("speaker_label", sa.String(length=100), nullable=False),
        sa.Column("matched_user_id", sa.String(length=100), nullable=True),
        sa.Column("matched_name", sa.String(length=255), nullable=True),
        sa.Column("similarity", sa.Float(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        schema=SCHEMA,
    )
    op.create_index(
        "idx_face_recognition_results_recording_id",
        "face_recognition_results",
        ["recording_id"],
        schema=SCHEMA,
    )

    op.create_table(
        "voice_authenticity_results",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("recording_id", sa.String(length=100), nullable=False),
        sa.Column("speaker_label", sa.String(length=100), nullable=False),
        sa.Column("start_seconds", sa.Float(), nullable=False),
        sa.Column("end_seconds", sa.Float(), nullable=False),
        sa.Column("label", sa.String(length=20), nullable=False),
        sa.Column("confidence", sa.Float(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        schema=SCHEMA,
    )
    op.create_index(
        "idx_voice_authenticity_results_recording_id",
        "voice_authenticity_results",
        ["recording_id"],
        schema=SCHEMA,
    )


def downgrade() -> None:
    op.drop_table("voice_authenticity_results", schema=SCHEMA)
    op.drop_table("face_recognition_results", schema=SCHEMA)
    op.drop_table("known_faces", schema=SCHEMA)
    op.execute(f"DROP SCHEMA IF EXISTS {SCHEMA} CASCADE")
