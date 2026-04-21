package es.um.sisdist.backend.Service;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.Writer;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

@WebServlet("/metrics")
public class PrometheusServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Le decimos a Prometheus que todo ha ido bien (HTTP 200 OK)
        resp.setStatus(HttpServletResponse.SC_OK);
        // Le decimos que el texto está en el formato especial que él entiende (004)
        resp.setContentType(TextFormat.CONTENT_TYPE_004);
        
        // Escribimos las métricas en la respuesta
        try (Writer writer = resp.getWriter()) {
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
        }
    }
}