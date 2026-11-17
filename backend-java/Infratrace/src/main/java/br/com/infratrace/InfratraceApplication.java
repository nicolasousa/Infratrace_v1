package br.com.infratrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal do sistema: é ela que liga o Spring Boot.
 *
 * @SpringBootApplication faz três coisas:
 *  - marca esta classe como fonte de configuração;
 *  - liga a autoconfiguração (ex.: sobe o Tomcat porque o spring-boot-starter-web está no pom);
 *  - procura componentes (@RestController, @Service, @Repository...) neste pacote
 *    (br.com.infratrace) e em todos os subpacotes. Por isso ela fica no pacote raiz.
 */
@SpringBootApplication
public class InfratraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfratraceApplication.class, args);
    }
}
