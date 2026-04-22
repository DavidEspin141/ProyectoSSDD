import unittest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


BASE_URL = "http://127.0.0.1:5010" # URL  del frontend
EMAIL = "admin@um.es"
PASSWORD = "2004"


class LlamaChatTest(unittest.TestCase):
    def setUp(self):
        options = webdriver.ChromeOptions()
        options.add_argument("--window-size=1280,800")

        self.driver = webdriver.Chrome(options=options)
        self.wait = WebDriverWait(self.driver, 20)

    def test_flujo_completo_login_y_chat(self):
        driver = self.driver
        wait = self.wait

        # 1) Ir a login
        driver.get(f"{BASE_URL}/login")

        # 2) Login
        wait.until(EC.presence_of_element_located((By.ID, "floatingInput"))).send_keys(EMAIL)
        driver.find_element(By.ID, "floatingPassword").send_keys(PASSWORD)
        driver.find_element(By.CSS_SELECTOR, "button.btn-login[type='submit']").click()

        # 3) Tras login: estamos en inicio. Pulsar "Chats" en la navbar.
        chats_link = wait.until(
            EC.element_to_be_clickable((By.XPATH, "//a[contains(@class,'nav-link') and normalize-space()='Chats']"))
        )
        chats_link.click()

        # 4) Ya en la página de chat: esperar elementos principales
        wait.until(EC.presence_of_element_located((By.ID, "chat-messages")))

        # 5) Enviar prompt
        input_box = wait.until(EC.element_to_be_clickable((By.ID, "message-input")))
        input_box.send_keys("Hola, ¿qué tal?")
        driver.find_element(By.ID, "send-button").click()

        # 6) Esperar respuesta del asistente
        def assistant_message_with_text(d):
            msgs = d.find_elements(By.CSS_SELECTOR, "#chat-messages .message.assistant .message-content")
            for m in reversed(msgs):
                txt = m.text.strip()
                if txt:
                    return m
            return False

        msg = WebDriverWait(driver, 320).until(assistant_message_with_text)
        print("Respuesta recibida:\n" + msg.text)

        self.assertTrue(msg.text.strip())
        self.assertNotIn("[ERROR]", msg.text)

    def tearDown(self):
        self.driver.quit()


if __name__ == "__main__":
    unittest.main(verbosity=2)