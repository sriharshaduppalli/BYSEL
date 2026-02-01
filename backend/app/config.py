import os
from dotenv import load_dotenv

load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./bysel.db")
API_HOST = os.getenv("API_HOST", "0.0.0.0")
API_PORT = int(os.getenv("API_PORT", 8000))
DEBUG = os.getenv("DEBUG", "True").lower() == "true"

SYMBOLS = ["RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN", "WIPRO", "BAJAJFINSV", "HDFC", "LT", "MARUTI"]
