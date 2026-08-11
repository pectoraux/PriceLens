from sqlalchemy import Column, Integer, String, Float, ForeignKey, BigInteger, DateTime, LargeBinary, ARRAY
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.sql import func
from app.core.database import Base

class Prototype(Base):
    __tablename__ = "prototype"

    id = Column(BigInteger, primary_key=True, index=True)
    item_id = Column(BigInteger, ForeignKey("taxonomy_item.id"), nullable=False)

    centroid = Column(LargeBinary, nullable=False) # 512 x int8
    n_members = Column(Integer, nullable=False)

    # Influence Tracing: required for reversibility
    contributor_ids = Column(ARRAY(UUID(as_uuid=True)), nullable=False)

    region_id = Column(BigInteger, nullable=True)
    model_version = Column(String, nullable=False)
    built_at = Column(DateTime(timezone=True), server_default=func.now())
