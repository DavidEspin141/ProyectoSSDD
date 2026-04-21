import requests
from flask import Flask, render_template, send_from_directory, url_for, request, redirect, flash
from flask_login import LoginManager, login_manager, current_user, login_user, login_required, logout_user
from flask import jsonify, request, session
from flask_login import login_required, current_user
from prometheus_flask_exporter import PrometheusMetrics

import requests
import os
# Usuarios
from models import users, User

# Login
from forms import LoginForm, SignupForm

REST_API_URL = "http://backend-rest:8080/Service/u"

app = Flask(__name__, static_url_path='')
metrics = PrometheusMetrics(app)  #Habilita endpoint /metrics

login_manager = LoginManager()
login_manager.init_app(app) # Para mantener la sesión

# Configurar el secret_key. OJO, no debe ir en un servidor git público.
# Python ofrece varias formas de almacenar esto de forma segura, que
# no cubriremos aquí.
app.config['SECRET_KEY'] = 'qH1vprMjavek52cv7Lmfe1FoCexrrV8egFnB21jHhkuOHm8hJUe1hwn7pKEZQ1fioUzDb3sWcNK1pJVVIhyrgvFiIrceXpKJBFIn_i9-LTLBCc4cqaI3gjJJHU6kxuT8bnC7Ng'

@app.route('/static/<path:path>')
def serve_static(path):
    return send_from_directory('static', path)

@app.route('/')
def index():
    return render_template('index.html')

def get_auth_headers():
    token = session.get('jwt_token')
    if token:
        return {'Authorization': f'Bearer {token}'}
    return {}

@app.route('/login', methods=['GET', 'POST'])
def login():
    if current_user.is_authenticated:
        return redirect(url_for('index'))
    else:
        error = None
        form = LoginForm(request.form)
        if request.method == "POST" and form.validate():
            payload = {
                "email": form.email.data,
                "password": form.password.data
            }
            
            try:
                # Enviamos las credenciales a Java para validar
                response = requests.post(f"{REST_API_URL}/login", json=payload, timeout=5)
                
                if response.status_code == 200:
                    user_data = response.json()
                    print("DEBUG user_data:", user_data)
                    # Guardamos el token JWT en sesión para usarlo en futuras llamadas al backend
                    session['jwt_token'] = user_data.get('token')
                    
                    # Instanciamos el usuario de Flask usando los datos que devolvió Java
                    user = User(user_data['id'], 
                                user_data['name'], 
                                user_data['email'], 
                                user_data.get('password', '').encode('utf-8'))
                    
                    # Lo añadimos a la lista en memoria de Flask-Login para mantener la sesión
                    users.append(user) 
                    login_user(user, remember=form.remember_me.data)
                    
                    return redirect(url_for('index'))
                else:
                    error = 'Credenciales inválidas.'
            except requests.exceptions.RequestException:
                error = "Error de conexión con el backend."

    return render_template('login.html', form=form, error=error)

@app.route('/signup', methods=['GET', 'POST'])
def signup():
    form = SignupForm(request.form)
    if request.method == 'POST' and form.validate():
        payload = {
            "name": form.name.data,
            "email": form.email.data,
            "password": form.password.data
        }
        
        try:
            # Enviamos el registro a Java
            response = requests.post(f"{REST_API_URL}/register", json=payload, timeout=5)
            print(f"DEBUG: Java respondió con código {response.status_code}") # <--- AÑADE ESTO
            if response.status_code == 200:
                flash("Cuenta creada con éxito. Ya puedes iniciar sesión.", "success")
                return redirect(url_for('login'))
            else:
                flash("El email ya existe o hubo un error.", "error")
        except requests.exceptions.RequestException:
            flash("Error de conexión con el backend.", "error")
            
    return render_template('signup.html', form=form)


@app.route('/profile')
@login_required
def profile():
    return render_template('profile.html')

@app.route('/chat')
@login_required
def chat():
    return render_template('chat.html')

@app.route('/api/chats', methods=['GET'])
@login_required
def api_chats():
    try:
        r = requests.get(f"{REST_API_URL}/{current_user.id}/dialogue", headers=get_auth_headers(), timeout=5)
        if r.status_code != 200:
            return jsonify({"error": "No se pudieron cargar las conversaciones"}), 502
        # backend-rest devuelve una lista de Conversation
        return jsonify(r.json()), 200
    except requests.exceptions.RequestException as e:
        return jsonify({"error": f"Error de conexión con backend-rest: {e}"}), 502


@app.route('/api/chats/<dialogue_id>', methods=['GET'])
@login_required
def api_chat(dialogue_id):
    try:
        r = requests.get(f"{REST_API_URL}/{current_user.id}/dialogue/{dialogue_id}", headers=get_auth_headers(), timeout=5)
        if r.status_code != 200:
            return jsonify({"error": "Conversación no encontrada"}), 404
        return jsonify(r.json()), 200
    except requests.exceptions.RequestException as e:
        return jsonify({"error": f"Error de conexión con backend-rest: {e}"}), 502
    
@app.route('/api/chats/<dialogue_id>', methods=['DELETE'])
@login_required
def delete_chat(dialogue_id):
    try:
        r = requests.delete(f"{REST_API_URL}/{current_user.id}/dialogue/{dialogue_id}", headers=get_auth_headers(), timeout=5)
        return jsonify({"status": "deleted"}), r.status_code
    except:
        return jsonify({"error": "No se pudo borrar"}), 502
    
@app.route('/send', methods=['POST'])
@login_required
def api_chat_send():
    data = request.get_json(silent=True) or {}
    prompt = (data.get('prompt') or '').strip()
    if not prompt:
        return jsonify({"error": "Campo 'prompt' vacío"}), 400

    user_id = str(current_user.id)

    # dialogue_id: viene del cliente o se guarda en sesión
    dialogue_id = (data.get('dialogue_id') or session.get('dialogue_id') or '').strip()

    base_url = f"{REST_API_URL}/{user_id}/dialogue"
    payload = {"prompt": prompt}

    try:
        if not dialogue_id:
            resp = requests.post(base_url, json=payload, headers=get_auth_headers(), timeout=180)
        else:
            resp = requests.post(f"{base_url}/{dialogue_id}/next", json=payload, headers=get_auth_headers(), timeout=180)

        # Si backend devuelve error
        if resp.status_code != 200:
            return jsonify({
                "error": "Backend REST devolvió error",
                "status_code": resp.status_code,
                "details": resp.text
            }), 502

        # Caso CLAVE: 200 pero cuerpo vacío
        if not resp.content or not resp.text.strip():
            return jsonify({
                "error": "Backend REST devolvió 200 pero sin contenido (Content-Length 0)",
                "status_code": resp.status_code,
                "dialogue_id": dialogue_id  # devolvemos el que tengamos
            }), 502

        # Intentar parsear JSON
        try:
            conv = resp.json()
        except ValueError:
            return jsonify({
                "error": "Backend REST devolvió contenido no-JSON",
                "status_code": resp.status_code,
                "raw": resp.text[:500]
            }), 502

        # Guardar el dialogue_id (cubriendo nombres posibles)
        new_dialogue_id = (
            conv.get("dialogue_id") or
            conv.get("dialogueId") or
            conv.get("id") or
            dialogue_id
        )
        if new_dialogue_id:
            session["dialogue_id"] = new_dialogue_id

        # Extraer última respuesta si viene dialogue[]
        last_response = None
        dialogue_list = conv.get("dialogue")
        if isinstance(dialogue_list, list) and dialogue_list:
            last = dialogue_list[-1]
            if isinstance(last, dict):
                last_response = last.get("response")

        return jsonify({
            "dialogue_id": new_dialogue_id,
            "response": last_response,
        }), 200

    except requests.exceptions.RequestException as e:
        return jsonify({"error": "Error conectando con backend REST", "details": str(e)}), 502

@app.route('/logout')
@login_required
def logout():
    session.pop('jwt_token', None)  # Limpiamos el token JWT de sesión
    logout_user()
    return redirect(url_for('index'))

@app.route('/chat/reset', methods=['POST'])
@login_required
def chat_reset():
    session.pop('dialogue_id', None)
    return '', 204

@login_manager.user_loader
def load_user(user_id):
    for user in users:
        # Comparamos directamente como strings
        if str(user.id) == str(user_id):
            return user
    return None

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0')
