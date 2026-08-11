from pydantic import BaseModel
from typing import Optional, Dict
from datetime import datetime

class ObservationSubmission(BaseModel):
    clientId: str
    nonce: str
    itemSlug: Optional[str] = None
    priceMinor: int
    currencyCode: str
    quantity: float
    unit: str
    geohash6: str
    geoConfidence: float
    captureSeal: str
    deviceClassId: str
    modelVersions: Dict[str, str]
    predictedItemSlug: Optional[str] = None
    predictedConfidence: Optional[float] = None
    labelWasCorrected: bool
    priceWasCorrected: bool
    abstained: bool
    evidenceUploadRequested: bool

class ObservationResponse(BaseModel):
    observationId: str
    evidenceUploadUrl: Optional[str] = None
    status: str
    shadowResult: Optional[Dict] = None
