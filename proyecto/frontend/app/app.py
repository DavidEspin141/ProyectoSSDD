import requests
from flask import Flask, render_template, send_from_directory, url_for, request, redirect, flash
from flask_login import LoginManager, login_manager, current_user, login_user, login_required, logout_user
# Usuarios
from models import users, User

# Login
from forms import LoginForm, SignupForm

REST_API_URL = "http://backend-rest:8080/Service/u"

app = Flask(__name__, static_url_path='')
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

@app.route('/logout')
@login_required
def logout():
    logout_user()
    return redirect(url_for('index'))

@login_manager.user_loader
def load_user(user_id):
    for user in users:
        # Comparamos directamente como strings
        if str(user.id) == str(user_id):
            return user
    return None

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0')
