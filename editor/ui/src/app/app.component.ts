import { Component } from '@angular/core';
import { APIService } from './api.service'
import { StackEntry, ToolsService, Tool} from './tools.service';
import { UserService, User } from './user.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'app';

  user: User | null = null;
  tools!: StackEntry;
  toolHasParent: boolean = false;
  
  constructor (private api: APIService, private userService: UserService, private toolsService: ToolsService) {
    userService.user.subscribe(u => this.user = u);
    toolsService.subscribe((ts, hasParent) => { this.tools = ts; this.toolHasParent = hasParent });
  }

  toolsBack() {
    this.toolsService.remove(this.tools.source);
  }

  logout(): void {
    this.userService.logout();
  }

} 
