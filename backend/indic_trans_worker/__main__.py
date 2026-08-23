import os

import uvicorn

if __name__ == "__main__":
    uvicorn.run(
        "indic_trans_worker.app:app",
        host=os.getenv("INDIC_TRANS_HOST", "127.0.0.1"),
        port=int(os.getenv("INDIC_TRANS_PORT", "8101")),
    )
