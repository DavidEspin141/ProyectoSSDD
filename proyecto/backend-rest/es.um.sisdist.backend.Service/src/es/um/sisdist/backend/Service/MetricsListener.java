package es.um.sisdist.backend.Service;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletContextEvent;
import io.prometheus.client.hotspot.DefaultExports;

@WebListener 
public class MetricsListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Activa las métricas internas de la Máquina Virtual de Java (CPU, RAM, GC...)
        DefaultExports.initialize();
    }
}