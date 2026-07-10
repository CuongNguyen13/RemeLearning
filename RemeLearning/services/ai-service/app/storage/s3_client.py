import tempfile

import boto3

from app.config import settings


def _client():
    kwargs = {"region_name": settings.s3_region}
    if settings.s3_endpoint:
        kwargs["endpoint_url"] = settings.s3_endpoint
    if settings.s3_access_key:
        kwargs["aws_access_key_id"] = settings.s3_access_key
        kwargs["aws_secret_access_key"] = settings.s3_secret_key
    return boto3.client("s3", **kwargs)


def download_to_tempfile(bucket: str, key: str) -> str:
    suffix = "." + key.rsplit(".", 1)[-1] if "." in key else ""
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    _client().download_fileobj(bucket, key, tmp)
    tmp.close()
    return tmp.name
