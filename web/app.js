'use strict';
const $ = id => document.getElementById(id);
async function api(path, options={}) {
  const res = await fetch('/api'+path, options);
  const body = await res.json();
  if (!res.ok) throw new Error(body.erro || 'Falha na solicitação');
  return body;
}
function post(path, campos) { return api(path,{method:'POST',body:new URLSearchParams(campos)}); }
function aviso(texto, erro=false) { const el=$('mensagem'); el.hidden=false; el.className=erro?'erro':'ok'; el.textContent=texto; }
function limparAviso(){ $('mensagem').hidden=true; }
function prioridade(valor){ const badge=document.createElement('span'); badge.className='prioridade '+({ALTA:'alta',MEDIA:'media',BAIXA:'baixa'}[valor]||''); badge.textContent=valor; return badge; }
function cartao(c) {
  const item=document.createElement('article'); item.className='chamado';
  const top=document.createElement('div'); top.className='chamado-top';
  const codigo=document.createElement('span'); codigo.className='codigo'; codigo.textContent='#'+c.id; top.append(codigo,prioridade(c.prioridade));
  const h=document.createElement('h3'); h.textContent=c.titulo;
  const descricao=document.createElement('p'); descricao.innerHTML=c.descricao;
  const rodape=document.createElement('div'); rodape.className='chamado-foot';
  const autor=document.createElement('span'); autor.textContent='Solicitante: '+c.autor;
  const btn=document.createElement('button'); btn.className='secondary'; btn.textContent='Ver detalhes'; btn.addEventListener('click',()=>detalhar(String(c.id)));
  rodape.append(autor,btn); item.append(top,h,descricao,rodape); return item;
}
async function listar(){
  try { const lista=await api('/chamados?q='+encodeURIComponent($('termo').value)); $('lista').replaceChildren(...lista.map(cartao));
    if(!lista.length){ const p=document.createElement('p'); p.className='empty'; p.textContent='Nenhuma solicitação encontrada.'; $('lista').append(p); }
    $('total').textContent=lista.length; $('alta').textContent=lista.filter(c=>c.prioridade==='ALTA').length;
  } catch(e){aviso(e.message,true);}
}
async function detalhar(id){
  $('detalhe').replaceChildren();
  try { const c=await api('/chamados/'+encodeURIComponent(id)); const item=cartao(c); item.querySelector('button').remove(); $('detalhe').append(item); }
  catch(e){aviso(e.message,true);}
}
async function selecionar(){
  limparAviso(); $('detalhe').replaceChildren(); $('termo').value='';
  try { const p=await post('/perfil',{perfil:$('perfil').value}); $('sessao').textContent=p.nome; await listar(); }
  catch(e){aviso(e.message,true);}
}
$('perfil').addEventListener('change',selecionar);
$('busca').addEventListener('submit',e=>{e.preventDefault(); limparAviso(); listar();});
$('atualizar').addEventListener('click',()=>{limparAviso(); listar();});
$('consulta').addEventListener('submit',e=>{e.preventDefault(); limparAviso(); detalhar($('protocolo').value);});
$('novo').addEventListener('submit',async e=>{ e.preventDefault(); limparAviso();
  try { const c=await post('/chamados',{titulo:$('titulo').value,descricao:$('descricao').value,prioridade:$('prioridade').value}); $('novo').reset(); $('termo').value=''; aviso('Chamado #'+c.id+' registrado.'); await listar(); }
  catch(err){aviso(err.message,true);}
});
selecionar();
