# Encontre a Vulnerabilidade — SeguraLab

## Contexto

O SeguraLab é uma central de chamados de um laboratório de tecnologia. A aplicação permite registrar solicitações, pesquisar chamados e consultar protocolos. O sistema está funcionando, mas uma revisão interna identificou comportamentos que comprometem a segurança dos dados.

Vocês fazem parte da equipe responsável por investigar os relatos, identificar as causas e corrigir o sistema. Diferentemente de uma atividade de implementação de login, o objetivo é verificar se uma aplicação funcional respeita seus limites de segurança, inclusive quando recebe entradas inesperadas.

## Ambiente e limites da atividade

- O sistema foi criado exclusivamente para esta atividade, com usuários e informações fictícios. Trabalhem apenas na própria cópia local, em `http://127.0.0.1:8788`.
- Não publiquem a aplicação em servidores ou exponham a porta para outros computadores. Não testem sistemas de terceiros, redes da instituição ou dados reais.
- Utilizem entradas de teste inofensivas. Não é necessário executar scripts, apagar dados ou acessar informações reais para comprovar as falhas.
- Os perfis de demonstração são selecionados na interface de propósito. Não representam um sistema real de autenticação. Não é necessário implementar login, senhas ou cadastro de usuários.
- O banco é SQLite em memória, com registros fictícios recriados ao iniciar o servidor. A persistência em disco não é requisito desta atividade.

## Como executar

### Requisitos

JDK 17 ou superior e um navegador. Não é necessário instalar Maven, servidor de banco de dados ou framework. O projeto utiliza as mesmas bibliotecas JDBC do ReservaLab.

Se os arquivos JAR já estiverem na pasta `lib/`, execute normalmente. Caso estejam ausentes, copie os três JARs da pasta `lib/` do ReservaLab para esta pasta, ou deixe o script baixá-los automaticamente na primeira execução (requer internet).

### Windows

Execute `executar.bat` na pasta do projeto e mantenha a janela do servidor aberta. Abra `http://127.0.0.1:8788` no navegador.

### Linux/macOS

Na pasta do projeto, execute `sh executar.sh` e abra o mesmo endereço.

Para encerrar, pressione Ctrl+C no terminal. Após alterar arquivos Java, encerre e execute novamente o servidor. Após alterar JavaScript ou CSS, recarregue a página. Reiniciar restaura o banco fictício.

## Funcionalidades e regras de negócio

- Beatriz (solicitante) pode abrir chamados e consultar exclusivamente os próprios registros. A regra vale tanto para a pesquisa quanto para a consulta direta por protocolo.
- Rafael (suporte) pode consultar todos os chamados. Ambos os perfis podem registrar novas solicitações; a autoria é determinada pelo servidor a partir da sessão.
- A busca deve localizar títulos que contenham o texto informado, sem distinguir maiúsculas de minúsculas nas letras de A a Z e sem tratar caracteres especiais como comandos ou curingas.
- Título: 3 a 80 caracteres; descrição: 5 a 400 caracteres, após remover espaços nas extremidades. Prioridades aceitas: `BAIXA`, `MEDIA` e `ALTA`.
- Os textos informados devem ser exibidos como texto, não como marcação HTML. Erros de entrada devem retornar mensagens compreensíveis, sem detalhes internos.
- A aplicação fornece registros de exemplo: protocolos 101 e 102 pertencem à Beatriz; 103 e 104 pertencem a outro solicitante. O suporte pode consultá-los todos.

## Chamados da revisão de segurança

### SEC-01 — Pesquisa não confiável

A equipe de suporte notou que determinados caracteres em uma busca podem alterar seu comportamento ou provocar erros inesperados. Uma busca deve tratar o conteúdo digitado como texto, e não permitir que ele interfira na consulta ao banco.

**Resultado esperado:** pesquisas comuns e com apóstrofos, sinais de porcentagem ou sublinhados funcionam normalmente; o resultado respeita o perfil selecionado. A correção deve separar a instrução SQL dos valores recebidos, não apenas bloquear caracteres.

### SEC-02 — Formatação inesperada

Um solicitante informou que parte da descrição de seu chamado foi exibida com uma formatação que ele não pretendia. O conteúdo registrado pelo usuário não deveria modificar a estrutura visual da página.

**Resultado esperado:** mesmo um texto contendo marcações simples, como `<b>Exemplo</b>`, aparece literalmente na listagem e no detalhe, sem criar elementos HTML a partir da entrada.

### SEC-03 — Registro fora do perfil

A equipe observou que a listagem e a consulta por protocolo nem sempre respeitam os mesmos limites de acesso.

**Resultado esperado:** Beatriz não pode receber informações de registros de terceiros, mesmo consultando um protocolo conhecido diretamente. Rafael continua conseguindo consultar os registros. O servidor deve aplicar a regra de acesso; ocultar um botão não é suficiente.

### SEC-04 — Solicitações inconsistentes

Algumas solicitações foram registradas com informações que não atendem às regras de negócio, apesar de o formulário possuir validações.

**Resultado esperado:** o servidor rejeita títulos e descrições vazios, curtos ou longos demais, e prioridades inexistentes, sem salvar registros inválidos. Chamados válidos continuam sendo cadastrados. A proteção deve funcionar mesmo quando os dados não passam pelo formulário HTML.

### SEC-05 — Informação interna em mensagens de erro

O suporte recebeu relatos de mensagens de erro contendo detalhes técnicos que não deveriam estar disponíveis aos usuários.

**Resultado esperado:** um protocolo inválido retorna uma resposta adequada (400), um protocolo inexistente retorna 404, e erros internos recebem uma mensagem genérica (500). Informações de diagnóstico ficam no servidor, não na resposta ao navegador.

## Investigação e desenvolvimento

1. Execute a aplicação inicial e explore as funcionalidades nos dois perfis de demonstração.
2. Reproduza os relatos com dados de teste inofensivos. Observe a aba Network das ferramentas do navegador, as respostas HTTP e o código Java/JavaScript. Use o debugger quando necessário.
3. Identifique a causa de cada problema e implemente as correções. Preserve as funcionalidades existentes e a estrutura do projeto.
4. Repita os testes antes e depois da correção. Inclua verificações de casos válidos e inválidos, inclusive no servidor. Para validar regras do back-end, podem utilizar requisições locais às rotas documentadas abaixo com dados fictícios.

## Contrato da aplicação

As respostas são JSON. As requisições POST usam os campos de formulário no formato `application/x-www-form-urlencoded`. O navegador envia automaticamente o cookie de sessão do perfil selecionado nas requisições locais.

| Método | Rota | Finalidade |
|---|---|---|
| POST | `/api/perfil` | Selecionar perfil de demonstração (`perfil=bia` ou `perfil=admin`) |
| GET | `/api/perfil` | Consultar perfil atual |
| GET | `/api/chamados?q=texto` | Listar e pesquisar chamados |
| GET | `/api/chamados/{id}` | Consultar um protocolo |
| POST | `/api/chamados` | Registrar chamado (`titulo`, `descricao`, `prioridade`) |

Respostas esperadas: 200 para consultas válidas, 201 para cadastro bem-sucedido, 400 para entradas inválidas, 401 quando não há sessão, 403 para acesso proibido, 404 para registro inexistente e 500 para falhas internas inesperadas.

## Entrega

Realizem a atividade em equipes de até quatro integrantes. Entreguem o projeto corrigido (ou o link do repositório da equipe, conforme orientação do professor) e um breve registro das verificações realizadas, usando a estrutura abaixo.

| Incidente | Causa identificada | Correção realizada | Evidência antes/depois |
|---|---|---|---|
| SEC-01 | | | |
| SEC-02 | | | |
| SEC-03 | | | |
| SEC-04 | | | |
| SEC-05 | | | |

Não é necessário incluir um relatório extenso. Usem exemplos concretos dos resultados obtidos. Não entreguem a pasta `build/`, que é gerada automaticamente.

## Critérios de avaliação

- Identificação das causas das vulnerabilidades e adequação das correções.
- Aplicação das regras de segurança no local correto, preservando as funcionalidades originais.
- Qualidade e organização do código, sem soluções que apenas ocultem os sintomas.
- Evidências de verificação de entradas válidas e inválidas e dos limites de acesso.
- Participação da equipe no desenvolvimento.

## Estrutura do projeto

```text
seguralab/
├── src/br/edu/seguralab/  Código Java
├── web/                  HTML, CSS e JavaScript
├── ferramentas/          Preparação das bibliotecas, se necessário
├── lib/                  Bibliotecas JDBC
├── executar.bat          Execução no Windows
├── executar.sh           Execução no Linux/macOS
├── .gitignore
└── README.md
```
