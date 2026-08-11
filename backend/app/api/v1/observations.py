from fastapi import APIRouter, Header, HTTPException, Depends
from typing import Optional
from sqlalchemy.orm import Session
from app.core.database import get_db
from app.models.observation import Observation
from app.schemas.observation import ObservationSubmission, ObservationResponse
from datetime import datetime
import uuid

router = APIRouter()

@router.post("/", response_model=ObservationResponse, status_code=202)
async def submit_observation(
    submission: ObservationSubmission,
    x_integrity_token: Optional[str] = Header(None),
    x_signature: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    # 1. Nonce validation (In production: check Redis)
    # 2. Signature verification
    # 3. Play Integrity token verification

    observation_id = uuid.uuid4()

    # Mock locality and item IDs for now (In production: lookup from slugs/geohash)
    new_obs = Observation(
        id=observation_id,
        item_id=1, # Mock
        locality_id=1, # Mock
        price_minor=submission.priceMinor,
        currency_code=submission.currencyCode,
        quantity=submission.quantity,
        unit=submission.unit,
        observed_at=datetime.utcnow(),
        device_class_id=submission.deviceClassId,
        model_versions=submission.modelVersions,
        predicted_item_id=1, # Mock
        predicted_confidence=submission.predictedConfidence,
        label_was_corrected=submission.labelWasCorrected,
        price_was_corrected=submission.priceWasCorrected,
        abstained=submission.abstained,
        status="pending"
    )

    db.add(new_obs)
    db.commit()

    return ObservationResponse(
        observationId=str(observation_id),
        status="accepted",
        evidenceUploadUrl="https://s3.pricelens.app/upload/" + str(observation_id) if submission.evidenceUploadRequested else None
    )
