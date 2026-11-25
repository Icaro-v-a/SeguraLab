package br.edu.seguralab;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class App {
    private static final int PORTA = 8788;
    private final Connection db;
    private final Map<String, Persona> sessoes = new HashMap<>();
    private record Persona(String id, String nome, boolean admin) {}
    private static final Persona ALUNA = new Persona("bia", "Beatriz — solicitante", false);
    private static final Persona ADMIN = new Persona("admin", "Rafael — suporte", true);
    private record Chamado(int id, String autor, String titulo, String descricao, String prioridade) {}
    private static final class Erro extends RuntimeException {
        final int status;
        Erro(int status, String msg) { super(msg); this.status = status; }
    }

    private App() throws SQLException {
        db = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = db.createStatement()) {
            s.executeUpdate("CREATE TABLE chamados (id INTEGER PRIMARY KEY, autor VARCHAR(30), titulo VARCHAR(200), descricao VARCHAR(2000), prioridade VARCHAR(20))");
        }
        inserir(101, "bia", "Projetor sem sinal", "O projetor da sala 04 nao reconhece o cabo HDMI.", "MEDIA");
        inserir(102, "bia", "Acesso ao laboratorio", "Solicito acesso ao laboratorio de redes.", "BAIXA");
        inserir(103, "leo", "Revisao de inventario", "Registro interno ficticio do setor de patrimonio.", "ALTA");
        inserir(104, "leo", "Substituicao de teclado", "Teclado da bancada 07 com defeito.", "MEDIA");
    }
    private void inserir(int id, String autor, String titulo, String descricao, String prioridade) throws SQLException {
        try (PreparedStatement p = db.prepareStatement("INSERT INTO chamados (id,autor,titulo,descricao,prioridade) VALUES (?,?,?,?,?)")) {
            p.setInt(1,id); p.setString(2,autor); p.setString(3,titulo); p.setString(4,descricao); p.setString(5,prioridade); p.executeUpdate();
        }
    }
    private static Chamado ler(ResultSet r) throws SQLException {
        return new Chamado(r.getInt("id"),r.getString("autor"),r.getString("titulo"),r.getString("descricao"),r.getString("prioridade"));
    }
    private List<Chamado> buscar(Persona pessoa, String termo) throws SQLException {
        String sql = "SELECT * FROM chamados WHERE " + (pessoa.admin ? "1=1" : "autor='" + pessoa.id + "'")
            + " AND LOWER(titulo) LIKE '%" + termo.toLowerCase(Locale.ROOT) + "%' ORDER BY id";
        List<Chamado> lista = new ArrayList<>();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) lista.add(ler(r));
        }
        return lista;
    }
    private Chamado detalhar(Persona pessoa, String numero) throws SQLException {
        int id = Integer.parseInt(numero);
        try (PreparedStatement p = db.prepareStatement("SELECT * FROM chamados WHERE id=?")) {
            p.setInt(1,id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new Erro(404,"Chamado nao encontrado.");
                Chamado c = ler(r);
                return c;
            }
        }
    }
    private Chamado criar(Persona pessoa, Map<String,String> form) throws SQLException {
        String titulo = form.getOrDefault("titulo", "");
        String descricao = form.getOrDefault("descricao", "");
        String prioridade = form.getOrDefault("prioridade", "MEDIA");
        int id;
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT MAX(id) FROM chamados")) { r.next(); id=r.getInt(1)+1; }
        inserir(id,pessoa.id,titulo,descricao,prioridade);
        return new Chamado(id,pessoa.id,titulo,descricao,prioridade);
    }
    private Persona persona(HttpExchange ex) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) for (String item: cookie.split(";")) {
            String[] kv = item.trim().split("=",2);
            if (kv.length==2 && kv[0].equals("LABSESSION") && sessoes.containsKey(kv[1])) return sessoes.get(kv[1]);
        }
        throw new Erro(401,"Selecione um perfil de demonstracao.");
    }
    private static String json(String s) {
        StringBuilder b=new StringBuilder("\"");
        for(char c:s.toCharArray()) switch(c) {
            case '\\' -> b.append("\\\\"); case '"' -> b.append("\\\""); case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
            default -> { if(c<32) b.append(String.format("\\u%04x",(int)c)); else b.append(c); }
        }
        return b.append('"').toString();
    }
    private static String json(Chamado c) {
        return "{\"id\":"+c.id+",\"autor\":"+json(c.autor)+",\"titulo\":"+json(c.titulo)+",\"descricao\":"+json(c.descricao)+",\"prioridade\":"+json(c.prioridade)+"}";
    }
    private static String json(Persona p) { return "{\"nome\":"+json(p.nome)+",\"admin\":"+p.admin+"}"; }
    private static Map<String,String> parametros(String raw) {
        Map<String,String> m=new HashMap<>();
        if(raw==null || raw.isEmpty()) return m;
        for(String parte:raw.split("&")) {
            String[] kv=parte.split("=",2);
            try { m.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),URLDecoder.decode(kv.length==2?kv[1]:"",StandardCharsets.UTF_8)); }
            catch(IllegalArgumentException e) { throw new Erro(400,"Parametros invalidos."); }
        }
        return m;
    }
    private static Map<String,String> corpo(HttpExchange ex) throws IOException {
        byte[] b=ex.getRequestBody().readNBytes(16385);
        if(b.length>16384) throw new Erro(413,"Requisicao muito grande.");
        return parametros(new String(b,StandardCharsets.UTF_8));
    }
    private static void resposta(HttpExchange ex,int status,String tipo,String texto) throws IOException {
        byte[] b=texto.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type",tipo+"; charset=UTF-8");
        ex.getResponseHeaders().set("Cache-Control","no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        ex.sendResponseHeaders(status,b.length); ex.getResponseBody().write(b);
    }
    private static void enviar(HttpExchange ex,int status,String texto) throws IOException { resposta(ex,status,"application/json",texto); }
    private static void falha(HttpExchange ex,int status,String msg) throws IOException { enviar(ex,status,"{\"erro\":"+json(msg)+"}"); }
    private synchronized void atender(HttpExchange ex) throws IOException {
        try {
            String path=ex.getRequestURI().getPath(), metodo=ex.getRequestMethod();
            String host=ex.getRequestHeaders().getFirst("Host");
            if(!Set.of("127.0.0.1:"+PORTA,"localhost:"+PORTA).contains(host==null?"":host)) throw new Erro(403,"Host fora do laboratorio.");
            String origin=ex.getRequestHeaders().getFirst("Origin");
            if(!metodo.equals("GET") && origin!=null && !Set.of("http://127.0.0.1:"+PORTA,"http://localhost:"+PORTA).contains(origin)) throw new Erro(403,"Origem fora do laboratorio.");
            if(metodo.equals("GET") && Set.of("/","/index.html","/app.js","/styles.css").contains(path)) {
                String arquivo=path.equals("/")?"index.html":path.substring(1);
                String tipo=arquivo.endsWith(".js")?"application/javascript":arquivo.endsWith(".css")?"text/css":"text/html";
                resposta(ex,200,tipo,Files.readString(Path.of("web",arquivo))); return;
            }
            if(path.equals("/api/perfil") && metodo.equals("POST")) {
                String perfil=corpo(ex).get("perfil");
                Persona p="admin".equals(perfil)?ADMIN:"bia".equals(perfil)?ALUNA:null;
                if(p==null) throw new Erro(400,"Perfil inexistente.");
                String token=UUID.randomUUID().toString(); sessoes.put(token,p);
                ex.getResponseHeaders().add("Set-Cookie","LABSESSION="+token+"; Path=/; HttpOnly; SameSite=Strict");
                enviar(ex,200,json(p)); return;
            }
            if(!path.startsWith("/api/")) throw new Erro(404,"Pagina inexistente.");
            Persona p=persona(ex);
            if(path.equals("/api/perfil") && metodo.equals("GET")) { enviar(ex,200,json(p)); return; }
            if(path.equals("/api/chamados") && metodo.equals("GET")) {
                String termo=parametros(ex.getRequestURI().getRawQuery()).getOrDefault("q","");
                if(termo.length()>120) throw new Erro(400,"Busca muito longa.");
                enviar(ex,200,"["+String.join(",",buscar(p,termo).stream().map(App::json).toList())+"]"); return;
            }
            if(path.equals("/api/chamados") && metodo.equals("POST")) { enviar(ex,201,json(criar(p,corpo(ex)))); return; }
            if(path.startsWith("/api/chamados/") && metodo.equals("GET")) { enviar(ex,200,json(detalhar(p,path.substring("/api/chamados/".length())))); return; }
            throw new Erro(404,"Rota inexistente.");
        } catch(Erro e) { falha(ex,e.status,e.getMessage()); }
        catch(Exception e) {
            StringWriter detalhes=new StringWriter(); e.printStackTrace(new PrintWriter(detalhes));
            falha(ex,500,"Falha interna | ambiente=LAB-FICTICIO | banco=jdbc:sqlite::memory: | " + detalhes);
        } finally { ex.close(); }
    }
    public static void main(String[] args) throws Exception {
        App app=new App();
        HttpServer s=HttpServer.create(new InetSocketAddress("127.0.0.1",PORTA),0);
        s.createContext("/",app::atender); s.start();
        System.out.println("SeguraLab - laboratorio local com dados ficticios. http://127.0.0.1:"+PORTA);
    }
}
