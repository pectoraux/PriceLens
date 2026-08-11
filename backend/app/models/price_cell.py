from sqlalchemy import Column, Integer, String, Float, ForeignKey, BigInteger, DateTime, Date, Boolean, JSON
from sqlalchemy.sql import func
from app.core.database import Base

class PriceCell(Base):
    __tablename__ = "price_cell"

    locality_id = Column(BigInteger, ForeignKey("locality.id"), primary_key=True)
    item_id = Column(BigInteger, ForeignKey("taxonomy_item.id"), primary_key=True)
    unit = Column(String, primary_key=True)
    week_start = Column(Date, primary_key=True)

    p10_minor = Column(BigInteger, nullable=False)
    p50_minor = Column(BigInteger, nullable=False)
    p90_minor = Column(BigInteger, nullable=False)
    currency_code = Column(CHAR(3), nullable=False)

    n_observations = Column(Integer, default=0)
    n_contributors = Column(Integer, default=0)
    total_weight = Column(Float, default=0.0)
    source_mix = Column(JSON, nullable=False) # {user, public, receipt}
    confidence = Column(String, default="INSUFFICIENT") # HIGH|MEDIUM|LOW|INSUFFICIENT
    is_published = Column(Boolean, default=False)

    computed_at = Column(DateTime(timezone=True), server_default=func.now())
