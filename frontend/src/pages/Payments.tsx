import './Payments.css';
import Phase3OperationsModule from './Phase3OperationsModule';

export default function PaymentsModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
  return <section className="menuPage menu-payments"><Phase3OperationsModule module="Payments" ready={ready} onNotice={onNotice} /></section>;
}
