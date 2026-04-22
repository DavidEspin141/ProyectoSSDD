import requests
import time

# Atacamos directamente al backend Java, saltándonos el Frontend
BASE_URL = "http://localhost:8080/Service/u"

# Generamos email único construido con la hora en la que se ejecuta el test
USER_EMAIL = f"test_client_{int(time.time())}@um.es"
USER_PASS = "1234"
USER_NAME = "Robot de Prueba REST"

def run_test():
    print("INICIANDO CLIENTE DE PRUEBA (API REST)")
    print("-" * 60)

    # 1) Hacemos el REGISTRO
    print("Regsitrando nuevo usuario: {USER_EMAIL}...")
    res = requests.post(f"{BASE_URL}/register", json = {
        "name" : USER_NAME,
        "email" : USER_EMAIL,
        "password" : USER_PASS
    })
    

    # 2) Hacemos el LOGIN del user creado
    print("\nHaciendo Login para obtener el JWT...")
    res = requests.post(f"{BASE_URL}/login", json={
        "email" : USER_EMAIL,
        "password" : USER_PASS
    })
    print(f" -> Status HTTP: {res.status_code}")

    if res.status_code != 200:
        print("Error en LOGIN. Abortando...")
        return
    
    # Si no hay error, obtenemos datos del usuario
    user_data = res.json()
    user_id = user_data.get("id")
    token = user_data.get("token")
    print(f"   -> Login exitoso! ID: {user_id}")
    print(f"   -> Token JWT obtenido: {token[:20]}... (truncado)")

    # Cabecera de autenticación obligatoria para el resto de peticiones
    headers = {"Authorization": f"Bearer {token}"}

    # Iniciar conversación
    print("\nCreando conversación y enviando el primer prompt...")
    primer_prompt = "Hola, dime algo breve."
    print(f"   -> Enviando: '{primer_prompt}'")
    print("   -> Esperando a la IA... (puede tardar un poco)")

    # Le ponemos el mismo timeout largo que el test e2e
    res = requests.post(f"{BASE_URL}/{user_id}/dialogue", 
                        json={"prompt": primer_prompt}, 
                        headers=headers, 
                        timeout=320)
    print(f"   -> Status HTTP: {res.status_code}")

    chat_data = res.json()
    # Dependiendo de cómo lo devuelva tu Java, pillamos el ID
    dialogue_id = chat_data.get("dialogue_id") or chat_data.get("id") or chat_data.get("dialogueId")
    print(f"   -> ID de la conversación: {dialogue_id}")

    # Extraemos la respuesta de la IA
    dialogue_list = chat_data.get("dialogue", [])
    if dialogue_list:
        ultima_respuesta = dialogue_list[-1].get("response") or dialogue_list[-1].get("answer")
        print(f"   -> Respuesta IA: {ultima_respuesta}")

    # 4. Mostrar la conversación completa 
    print("\nPidiendo el historial completo al servidor...")
    res = requests.get(f"{BASE_URL}/{user_id}/dialogue/{dialogue_id}", headers=headers)
    print(f"   -> Status HTTP: {res.status_code}")
    print("   -> Historial devuelto:")
    
    historial = res.json().get("dialogue", [])
    for msg in historial:
        print(f"      * Yo: {msg.get('prompt')}")
        print(f"      * IA: {msg.get('response') or msg.get('answer')}")

    # 5. Eliminar la conversación
    print("\nEliminando la conversación...")
    res = requests.delete(f"{BASE_URL}/{user_id}/dialogue/{dialogue_id}", headers=headers)
    print(f"   -> Status HTTP: {res.status_code}")
    if res.status_code in [200, 204]:
        print("   -> Conversación borrada con éxito de la base de datos!")

    print("-" * 60)
    print("TEST DEL CLIENTE REST COMPLETADO AL 100%")

if __name__ == "__main__":
    run_test()

