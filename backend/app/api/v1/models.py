from fastapi import APIRouter
from app.schemas.models import ModelManifest, ModelEntry

router = APIRouter()

@router.get("/manifest", response_model=ModelManifest)
async def get_models_manifest(deviceClassId: str, abi: str):
    # Mock response for now
    return ModelManifest(
        models=[
            ModelEntry(
                name="detector",
                version="1.0.0",
                url="https://models.pricelens.app/yolo_world_tiny_int8.tflite",
                sha256="fake_detector_sha256",
                signature="fake_sig",
                minRamMb=1024,
                recommendedDelegate="gpu"
            ),
            ModelEntry(
                name="embedder",
                version="1.0.0",
                url="https://models.pricelens.app/mobile_clip_s2_int8.tflite",
                sha256="fake_embedder_sha256",
                signature="fake_sig",
                minRamMb=2048,
                recommendedDelegate="gpu"
            )
        ]
    )
