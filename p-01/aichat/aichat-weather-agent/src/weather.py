import os;
import requests;
import json;
from mcp.server.fastmcp import FastMCP;
from dotenv import load_dotenv;

load_dotenv();

WEATHER_API_KEY = os.getenv("WEATHER_API_KEY");
WEATHER_API_BASE_URL = os.getenv("WEATHER_API_BASE_URL");

mcp = FastMCP();

print(f"WEATHER_API_KEY: {WEATHER_API_KEY}");
print(f"WEATHER_API_BASE_URL: {WEATHER_API_BASE_URL}");


if __name__ == "__main__":
    print(f"main function");