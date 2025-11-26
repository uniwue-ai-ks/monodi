import { Input, Output, EventEmitter, Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-dragger',
  templateUrl: './dragger.component.html',
  styleUrls: ['./dragger.component.css']
})
export class DraggerComponent implements OnInit {
  @Input()
  zipper!: number[];

  @Input()
  actions!: string[];

  @Output()
  actionRequested = new EventEmitter<string>();

  @Output()
  dropRequested = new EventEmitter<DragRequest>();

  constructor() { }

  ngOnInit() {
  }

  dragstart(ev: DragEvent): void {
    ev.dataTransfer!.setData("text/plain", JSON.stringify(this.zipper));
    ev.dataTransfer!.dropEffect = "move";
  }

  dragover(ev: DragEvent): void {
    ev.preventDefault();
  }

  drop(ev: DragEvent): void {
    ev.preventDefault();
    this.dropRequested.emit({
      from: JSON.parse(ev.dataTransfer!.getData("text/plain")),
      to: this.zipper
    });
  }
}

export interface DragRequest {
  from: number[];
  to: number[];
}
