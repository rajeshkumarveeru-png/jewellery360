import './GoldRates.css';
import GenericModule from './GenericModule';

export default function GoldRatesModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
  return <section className="menuPage menu-gold-rates-rates"><GenericModule module="Gold & Rates" ready={ready} onNotice={onNotice} /></section>;
}
