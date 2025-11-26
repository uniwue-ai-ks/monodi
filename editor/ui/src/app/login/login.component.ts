import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { APIService } from '../api.service';
import { UserService } from '../user.service';
import { assertNever } from '../../utils';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  mail: string = "";
  password: string = "";

  constructor(private api: APIService, private users: UserService, private toastr: ToastrService, private router: Router) { }

  onSubmit(): void {
    this.api.login(this.mail, this.password).subscribe(res => {
      switch (res.kind) {
        case 'LoginFailed': this.toastr.error("Falscher Nutzername oder falsches Passwort", "Login fehlgeschlagen!"); break;
        case 'InvalidUsernameFormat': this.toastr.error("Nutzername muss eine Email Adresse sein", "Login fehlgeschlagen!"); break;
        case 'LoginSuccessful': {
          this.users.setUser({ user: res.user, roles: res.roles, token: res.token });
          this.toastr.success("Login Erfolgreich!");
          this.router.navigate(['/sources']);
          break;
        }
        default: assertNever(res);
      }
    });
  }
}
