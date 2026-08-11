from pydantic import BaseModel
from typing import List, Optional

class Bundle(BaseModel):
    kind: str
    url: str
    sha256: str
    sizeBytes: int
    signature: str

class CatalogManifest(BaseModel):
    taxonomyVersion: str
    prototypeIndexVersion: str
    calibrationVersion: str
    bundles: List[Bundle]
