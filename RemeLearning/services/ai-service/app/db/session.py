from contextlib import contextmanager

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.config import settings

# One engine for the process, same shape as the lazy-singleton engines in app/api/routes.py
# (whisper/diarization/vision) - created on first use, not at import time, so importing this
# module never requires Postgres to already be reachable.
_engine = None
_SessionLocal: sessionmaker | None = None


def _get_engine():
    global _engine, _SessionLocal
    if _engine is None:
        _engine = create_engine(settings.ai_database_url, pool_pre_ping=True, future=True)
        _SessionLocal = sessionmaker(bind=_engine, expire_on_commit=False, future=True)
    return _engine


@contextmanager
def session_scope() -> Session:
    """Provides a transactional SQLAlchemy session, committing on success and rolling back
    on any exception - the ORM-session equivalent of MyBatis's @Transactional-wrapped calls
    on the Java side."""
    _get_engine()
    assert _SessionLocal is not None
    session = _SessionLocal()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
