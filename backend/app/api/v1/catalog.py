from fastapi import APIRouter
from typing import Optional
from app.schemas.catalog import CatalogManifest, Bundle

router = APIRouter()

@router.get("/manifest", response_model=CatalogManifest)
async def get_catalog_manifest(locale: str, geohash6: str, since: Optional[str] = None):
    # Mock response for now
    return CatalogManifest(
        taxonomyVersion="1.0.0",
        prototypeIndexVersion="1.0.0",
        calibrationVersion="1.0.0",
        bundles=[
            Bundle(
                kind="taxonomy",
                url="https://cdn.pricelens.app/bundles/taxonomy_1.0.0.json",
                sha256="fake_sha256",
                sizeBytes=1024 * 500,
                signature="fake_signature"
            )
        ]
    )
