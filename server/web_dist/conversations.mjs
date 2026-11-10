// Private, memory-only client cache. Server messages and requests are authoritative.
export const pendingRequest = request => ['queued','running'].includes(request?.status);
export class ConversationStore {
 constructor(request,uuid=()=>crypto.randomUUID()) {this.request=request;this.uuid=uuid;this.entries=new Map();this.epoch=0;}
 entry(id) {
  if(!this.entries.has(id))this.entries.set(id,{conversation:null,messages:[],draft:'',draftVersion:0,mode:'question',loaded:false,loading:false,error:'',admission:null,sending:false,has_more:false,before_cursor:null,context_compacted:false});
  return this.entries.get(id);
 }
 clear(){this.epoch++;this.entries.clear();}
 setDraft(id,value){const e=this.entry(id);e.draft=value;e.draftVersion++;}
 setMode(id,value){this.entry(id).mode=value;}
 metadata(conversation) {
  const e=this.entry(conversation.id);
  const previous=e.conversation;
  if(!previous){e.conversation=conversation;return e;}
  const next=conversation.revision>=previous.revision?{...previous,...conversation}:{...previous};
  // Title workers have their own revision; a higher chat revision cannot revive an old title.
  const titleNewer=(conversation.title_revision??-1)>(previous.title_revision??-1);
  const manualWins=previous.title_origin==='manual'&&conversation.title_origin!=='manual';
  if(manualWins||(!titleNewer&&(conversation.title_revision??-1)<(previous.title_revision??-1))){for(const key of ['title','title_revision','title_origin'])if(key in previous)next[key]=previous[key];}
  else if(titleNewer){for(const key of ['title','title_revision','title_origin'])if(key in conversation)next[key]=conversation[key];}
  e.conversation=next;
  return e;
 }
 merge(e,messages) {
  const all=new Map(e.messages.map(m=>[m.id,m]));
  for(const m of messages)all.set(m.id,m);
  e.messages=[...all.values()].sort((a,b)=>a.id-b.id);
 }
 async load(id,{older=false}={}) {
  const e=this.entry(id),epoch=this.epoch;
  if(e.loading){await e.loadingDone;if(epoch!==this.epoch)return;return this.load(id,{older});}
  let release;e.loadingDone=new Promise(resolve=>{release=resolve;});
  e.loading=true;e.error='';
  try {
   const result=await this.request('/api/conversations/'+encodeURIComponent(id)+'?limit=40'+(older&&e.before_cursor?'&before='+encodeURIComponent(e.before_cursor):''));
   if(epoch!==this.epoch)return;
   // A disconnected latest window can hide a gap even after history was exhausted.
   // Restart the backwards walk at its server cursor; overlap is deduplicated, never inferred.
   // Admission deltas are not proof of page coverage; compare full latest windows only.
   const known=new Set(e.latest_page_ids||[]);
   const disconnected=!older && result.messages.length>0 && !result.messages.some(m=>known.has(m.id));
   if(!older)e.latest_page_ids=result.messages.map(m=>m.id);
   this.metadata(result.conversation);this.merge(e,result.messages);
   if(older||!e.loaded||disconnected){e.has_more=result.has_more;e.before_cursor=result.before_cursor;}
   e.context_compacted=result.context_compacted;e.loaded=true;
   return e;
  }catch(error){if(epoch===this.epoch)e.error=error.message;throw error;}
  finally{if(epoch===this.epoch)e.loading=false;release();}
 }
 async send(id,{repeat=false,content,mode}={}) {
  const e=this.entry(id),epoch=this.epoch;
  if(e.sending||pendingRequest(e.conversation?.latest_request))throw new Error('A request is already pending in this discussion.');
  if(e.admission&&!repeat)throw new Error('Check the uncertain request before sending another message.');
  if(e.conversation?.archived)throw new Error('Reopen this discussion before sending.');
  if(!repeat) {
   const text=content??e.draft,chosenMode=mode??e.mode;
   if(!text.trim()||[...text].length>8000)throw new Error('Message must contain 1–8000 characters.');
   if(!['question','email_draft'].includes(chosenMode))throw new Error('Invalid conversation mode.');
   if(!e.loaded)throw new Error('Load the discussion before sending.');
   e.admission={payload:{request_id:this.uuid(),content:text,mode:chosenMode,expected_revision:e.conversation.revision},draftVersion:content===undefined?e.draftVersion:null};
  }
  if(!e.admission)throw new Error('There is no uncertain request to retry.');
  const admission=e.admission;e.sending=true;e.error='';
  try {
   const result=await this.request('/api/conversations/'+encodeURIComponent(id)+'/messages',{method:'POST',body:admission.payload});
   if(epoch!==this.epoch)return;
   this.metadata(result.conversation);e.conversation.latest_request=result.request;this.merge(e,[result.user_message]);
   if(admission.draftVersion===e.draftVersion)e.draft='';
   e.admission=null;return result;
  }catch(error){
   if(epoch===this.epoch){e.error=error.message;if(error.status&&error.status<500)e.admission=null;}
   throw error;
  }finally{if(epoch===this.epoch)e.sending=false;}
 }
 async poll(id) {
  const e=this.entry(id),epoch=this.epoch,r=e.conversation?.latest_request;
  if(!pendingRequest(r))return;
  const result=await this.request('/api/conversations/'+encodeURIComponent(id)+'/requests/'+encodeURIComponent(r.id));
  if(epoch!==this.epoch)return;
  if(e.conversation.latest_request?.id!==r.id)return;
  e.conversation.latest_request=result;
  if(!pendingRequest(result))await this.load(id);
  return result;
 }
 async recover(id) {
  const e=this.entry(id),epoch=this.epoch,a=e.admission;
  if(!a)return this.load(id);
  const result=await this.request('/api/conversations/'+encodeURIComponent(id)+'/requests/'+encodeURIComponent(a.payload.request_id));
  if(epoch!==this.epoch)return;
  // An admitted request survives transport failure. Loading detail recovers its persisted user turn.
  if(a.draftVersion===e.draftVersion)e.draft='';
  e.admission=null;await this.load(id);return result;
 }
 async edit(id,changes) {
  const e=this.entry(id),epoch=this.epoch;
  const result=await this.request('/api/conversations/'+encodeURIComponent(id),{method:'PATCH',body:{expected_revision:e.conversation.revision,...changes}});
  if(epoch!==this.epoch)return;
  this.metadata(result);return result;
 }
}
