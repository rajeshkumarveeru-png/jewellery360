import './WhatsApp.css';
import GenericModule from './GenericModule';

export default function WhatsAppModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
  return <section className="menuPage menu-whatsapp"><GenericModule module="WhatsApp" ready={ready} onNotice={onNotice} /></section>;
}
